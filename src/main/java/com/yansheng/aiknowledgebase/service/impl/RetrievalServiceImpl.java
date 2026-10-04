package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.OutlineMapper;
import com.yansheng.aiknowledgebase.service.RerankService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    /** 检索结果缓存 TTL:知识库查询是典型读多写少,短 TTL + 上传失效双保险 */
    private static final long RESULT_CACHE_TTL_MINUTES = 5;

    /**
     * B-114 Phase2 树路由加权:query 切词后的最小词长与最大词数。
     * 只取长度 ≥2 的词(单字/符号噪声大,易误提权);最多 5 个词,避免 OR 条件爆炸与误命中。
     * 命中节点上限 50:锚定 chunk 集合只做"提权/前置",不改变召回规模,过宽的匹配交由 rerank 兜底。
     */
    private static final int TREE_MIN_KEYWORD_LENGTH = 2;
    private static final int TREE_MAX_KEYWORDS = 5;
    private static final int TREE_MAX_NODES = 50;
    /** 仅路由召回补入、未被 rerank 选中的锚定项的"前置"名额上限(防泛词误命中越权 min-score 灌满 topK) */
    private static final int TREE_FALLBACK_ANCHOR_QUOTA = 1;

    private final VectorSearchService vectorSearchService;
    private final RerankService rerankService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChunkMapper chunkMapper;
    private final com.yansheng.aiknowledgebase.mapper.FileMapper fileMapper;
    /** B-114 Outline 导航层只读查询(树路由加权用;失败降级不影响主链路) */
    private final OutlineMapper outlineMapper;

    @Value("${retrieval.top-k}")
    private int topK;

    @Value("${retrieval.similarity-threshold}")
    private double similarityThreshold;

    @Value("${retrieval.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Value("${retrieval.hybrid.enabled:true}")
    private boolean hybridEnabled;

    /** B-114 Phase2 树路由加权开关:默认 false —— 关闭时与 Sprint 8(纯 RAG)行为逐位一致(零回归) */
    @Value("${retrieval.tree-boost.enabled:false}")
    private boolean treeBoostEnabled;

    public RetrievalServiceImpl(VectorSearchService vectorSearchService,
                                RerankService rerankService,
                                RedisTemplate<String, Object> redisTemplate,
                                ChunkMapper chunkMapper,
                                com.yansheng.aiknowledgebase.mapper.FileMapper fileMapper,
                                OutlineMapper outlineMapper) {
        this.vectorSearchService = vectorSearchService;
        this.rerankService = rerankService;
        this.redisTemplate = redisTemplate;
        this.chunkMapper = chunkMapper;
        this.fileMapper = fileMapper;
        this.outlineMapper = outlineMapper;
    }

    @Override
    public List<SearchResult> retrieveTopK(String queryText) {
        // 缓存:同用户 + 同问题(归一化)直接命中,省去重复的 embedding/检索/重排开销
        String cacheKey = resultCacheKey(queryText);
        List<SearchResult> cached = readCache(cacheKey);
        if (cached != null) {
            log.info("检索缓存命中, key={}", cacheKey);
            // 旧缓存条目无 fileName(字段后加),命中也补一遍,引用面板不退化为"资料 N"
            fillFileNames(cached);
            return cached;
        }

        // 1. 粗召回(向量检索,多召回一些留给重排)
        //    多用户隔离:web 请求带用户上下文时,只在该用户拥有的文件范围内召回(防横向越权)
        Long userId = UserContext.getUserId();
        List<SearchResult> rawResults;
        try {
            rawResults = (userId != null)
                    ? vectorSearchService.searchForUser(queryText, topK * 3, userId)
                    : vectorSearchService.search(queryText, topK * 3);
        } catch (Exception e) {
            // 供应商故障降级:向量库不可用(额度过期/网络异常)时退化为 BM25 单路,问答不中断
            log.error("向量检索失败,降级为BM25单路: userId={}, error={}", userId, e.getMessage());
            rawResults = List.of();
        }

        // 2. 阈值过滤(去掉明显不相关的噪声邻居)——只对向量路(score 是距离,越小越相关)
        List<SearchResult> filtered = rawResults.stream()
                .filter(r -> r.getScore() <= similarityThreshold)
                .collect(Collectors.toList());

        // 3. 混合检索:BM25 全文检索并入(精确匹配/专有名词兜底,按用户文件范围)
        //    BM25 的 score 是相关度(越大越相关),语义与向量距离相反,不过阈值,直接并入去重
        if (hybridEnabled && userId != null) {
            List<SearchResult> bm25Results = chunkMapper.selectByFullText(userId, queryText, topK * 3);
            if (bm25Results != null && !bm25Results.isEmpty()) {
                Map<Long, SearchResult> merged = new LinkedHashMap<>();
                for (SearchResult r : filtered) {
                    merged.put(r.getChunkId(), r);
                }
                for (SearchResult r : bm25Results) {
                    merged.putIfAbsent(r.getChunkId(), r);
                }
                filtered = new ArrayList<>(merged.values());
                log.info("混合检索:向量 {} 条 + BM25 {} 条 → 合并 {} 条, userId={}",
                        rawResults.size(), bm25Results.size(), filtered.size(), userId);
            }
        }

        // 3.5 B-114 Phase2 树路由召回:命中标题树锚定的 chunk 补入候选池。
        //     关键:补召回必须发生在 rerank 截断之前,否则锚定 chunk 会被 rerank 丢弃 → 提权无效。
        //     开关关闭 / 无用户 / 无命中 / 任一步异常 → 降级纯 RAG(池子原样,后续与 Sprint 8 逐位一致)。
        List<SearchResult> pool = filtered;
        Set<Long> treeAnchorChunkIds = Collections.emptySet();
        if (treeBoostEnabled && userId != null) {
            try {
                TreeBoostResult boost = enrichPoolWithTreeAnchors(queryText, filtered, userId);
                pool = boost.pool();
                treeAnchorChunkIds = boost.anchorChunkIds();
            } catch (Exception e) {
                // 树检索失败不得中断问答:对齐 RerankServiceImpl 的 warn 降级风格
                log.warn("标题树路由召回失败,降级为纯 RAG: userId={}, error={}", userId, e.getMessage());
                pool = filtered;
                treeAnchorChunkIds = Collections.emptySet();
            }
        }

        // 4. 重排(精排):粗召回 → 交叉编码器重打分 → 取 topK;失败降级按原分排序
        List<SearchResult> finalResults;
        if (rerankEnabled && !pool.isEmpty()) {
            finalResults = rerankService.rerank(queryText, pool, topK);
        } else {
            // 兜底排序(无重排时):保持合并顺序——向量段(DashVector 距离升序)在前,
            // BM25 段(SQL 相关度降序)在后,两段各自天然有序。
            // 不可整体按 score 排序:两路 score 语义相反(距离 vs 相关度),混排必错一路。
            finalResults = pool.size() > topK
                    ? new ArrayList<>(pool.subList(0, topK))
                    : pool;
        }

        // 4.5 树提权/前置:命中锚定 chunk 稳定前移,保证能进入最终 topK(「预留槽位」)。
        //     命中项若已被 rerank 截断丢弃,则从补召回池中找回 → 锚定 chunk 必进 topK。
        //     与 3.5 同级降级:提权任一步失败只 log.warn,返回未加权的 rerank/兜底结果,问答不中断。
        if (treeBoostEnabled && userId != null && !treeAnchorChunkIds.isEmpty()) {
            try {
                finalResults = applyTreeBoost(pool, finalResults, treeAnchorChunkIds);
            } catch (Exception e) {
                log.warn("标题树提权前置失败,降级为未加权结果: userId={}, error={}", userId, e.getMessage());
                // finalResults 保持未加权原值(不重新赋值),语义不变
            }
        }

        fillFileNames(finalResults);
        writeCache(cacheKey, finalResults);
        return finalResults;
    }

    /**
     * B-114 Phase2 树路由召回:定位节点 → 取锚定 chunk → 不在候选池内的按 chunkId 点查补入。
     *
     * 匹配口径:query 按空白/标点切词后,只保留长度 ≥ {@link #TREE_MIN_KEYWORD_LENGTH} 的词(最多
     * {@link #TREE_MAX_KEYWORDS} 个),以 title / heading_path 的 LIKE 匹配定位「当前用户文件范围」内的节点
     * (归属链路 knowledge_outline_node → knowledge_file → knowledge,校验 knowledge.user_id)。
     * 只对 md 建树的文件有效;无结构文档(如 pdf)建不出树 → 天然无加成。
     *
     * 说明:本方法只"补召回 + 返回锚定 id",不改动候选池内既有条目与分数;真正的前置在
     * {@link #applyTreeBoost} 中完成(rerank 之后),故即便锚定 chunk 被 rerank 低分淘汰,
     * 也能借补召回池找回并前置。
     */
    private TreeBoostResult enrichPoolWithTreeAnchors(String query, List<SearchResult> pool, Long userId) {
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            log.warn("标题树路由:query 无可匹配词(切词后为空),降级纯 RAG: userId={}", userId);
            return new TreeBoostResult(pool, Collections.emptySet());
        }

        // 1. 定位节点(用户文件范围 + LIKE 匹配 title/heading_path)
        List<Long> nodeIds = outlineMapper.selectNodeIdsByUserKeywords(userId, keywords, TREE_MAX_NODES);
        if (nodeIds == null || nodeIds.isEmpty()) {
            log.warn("标题树路由:未命中 outline 节点,降级纯 RAG: userId={}, keywords={}", userId, keywords);
            return new TreeBoostResult(pool, Collections.emptySet());
        }

        // 2. 取锚定 chunk(nodeIds 批量查,空集合不查库)
        List<Long> anchorIds = outlineMapper.selectChunkIdsByNodeIds(nodeIds);
        if (anchorIds == null || anchorIds.isEmpty()) {
            log.warn("标题树路由:命中节点但无关联 chunk,降级纯 RAG: userId={}, nodeCount={}", userId, nodeIds.size());
            return new TreeBoostResult(pool, Collections.emptySet());
        }
        Set<Long> anchorSet = new LinkedHashSet<>(anchorIds);

        // 3. 路由召回:锚定 chunk 不在候选池内 → 按 chunkId 点查补入(点查同样校验归属该用户)
        Set<Long> poolChunkIds = pool.stream()
                .map(SearchResult::getChunkId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<Long> missing = anchorIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> !poolChunkIds.contains(id))
                .distinct()
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            log.info("标题树路由:命中节点 {} 个,锚定 chunk {} 条(均已在候选池内)", nodeIds.size(), anchorSet.size());
            return new TreeBoostResult(pool, anchorSet);
        }

        List<SearchResult> extra = chunkMapper.selectSearchResultsByIds(userId, missing);
        if (extra == null || extra.isEmpty()) {
            log.warn("标题树路由:锚定 chunk 点查为空(疑似归属不符/已删除),降级纯 RAG: userId={}, missing={}",
                    userId, missing.size());
            return new TreeBoostResult(pool, Collections.emptySet());
        }
        List<SearchResult> enriched = new ArrayList<>(pool);
        enriched.addAll(extra);
        log.info("标题树路由召回:命中节点 {} 个,补入候选 {} 条, userId={}", nodeIds.size(), extra.size(), userId);
        return new TreeBoostResult(enriched, anchorSet);
    }

    /**
     * B-114 Phase2 稳定前置:命中锚定 chunk 优先且靠前,其余保持 reranked 原相对顺序,最后截断到 topK。
     *
     * 防越权 min-score(评审 medium):树前置不得把 rerank 判为不相关的项顶进 topK ——
     *  - 已被 rerank 评分且未被淘汰的锚定项(出现在 reranked 里):行内项,可自由前置;
     *  - 仅靠「路由召回」补入池子、未被 rerank 选中的锚定项:最多前置 1 个(预留槽位只在无 rerank
     *    结果覆盖时兜底),防止 LIKE 泛词误命中把 topK 灌满。
     * 命中项顺序 = 先按其在 reranked 中的顺序,再按其在补召回池中的顺序。
     * topK 设计:生产 topK=5;锚定前置至多占 min(锚定数, topK) 位,不改变非锚定结果的相对排序。
     */
    private List<SearchResult> applyTreeBoost(List<SearchResult> pool, List<SearchResult> reranked, Set<Long> anchorChunkIds) {
        if (anchorChunkIds == null || anchorChunkIds.isEmpty()) {
            return reranked;
        }
        LinkedHashMap<Long, SearchResult> ordered = new LinkedHashMap<>();
        // 已被 rerank 评分且未淘汰的锚定项:自由前置(顺序按其 reranked 排名)
        for (SearchResult r : reranked) {
            if (r.getChunkId() != null && anchorChunkIds.contains(r.getChunkId())) {
                ordered.putIfAbsent(r.getChunkId(), r);
            }
        }
        // 仅路由召回补入、未被 rerank 选中的锚定项:限流最多 1 个(防泛词误命中灌满 topK)
        int fallbackQuota = TREE_FALLBACK_ANCHOR_QUOTA;
        for (SearchResult r : pool) {
            if (fallbackQuota <= 0) {
                break;
            }
            if (r.getChunkId() != null && anchorChunkIds.contains(r.getChunkId())
                    && !ordered.containsKey(r.getChunkId())) {
                ordered.put(r.getChunkId(), r);
                fallbackQuota--;
            }
        }
        // 其余保持 reranked 原相对顺序(过滤 chunkId == null 的条目,避免污染合并结果)
        for (SearchResult r : reranked) {
            if (r.getChunkId() != null) {
                ordered.putIfAbsent(r.getChunkId(), r);
            }
        }
        List<SearchResult> merged = new ArrayList<>(ordered.values());
        List<SearchResult> result = merged.size() > topK
                ? new ArrayList<>(merged.subList(0, topK))
                : merged;
        long inTopK = result.stream()
                .filter(r -> r.getChunkId() != null && anchorChunkIds.contains(r.getChunkId()))
                .count();
        log.info("标题树提权前置:锚定 {} 条,最终 topK 内命中 {} 条", anchorChunkIds.size(), inTopK);
        return result;
    }

    /** query 切词:按空白/标点/符号切分,仅保留长度 ≥2 的词,去重后最多取 TREE_MAX_KEYWORDS 个 */
    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(query.split("[\\s\\p{P}\\p{S}]+"))
                .map(String::trim)
                .filter(w -> w.length() >= TREE_MIN_KEYWORD_LENGTH)
                .distinct()
                .limit(TREE_MAX_KEYWORDS)
                .collect(Collectors.toList());
    }

    /** 树路由结果:补召回后的候选池 + 锚定 chunkId 集合(集合用于最终稳定前置;池子用于找回被截断的锚定项) */
    private record TreeBoostResult(List<SearchResult> pool, Set<Long> anchorChunkIds) {
    }

    /** 填充引用元数据(文件名 + 切片序号);批量点查,topK 规模下至多一两条 SQL */
    private void fillFileNames(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return;
        Map<Long, String> nameCache = new LinkedHashMap<>();
        // chunkIndex 补齐:向量检索(DashVector)不回传 chunk_index,按 chunkId 批量查库补齐
        List<Long> missingIndex = results.stream()
                .filter(r -> r.getChunkId() != null && r.getChunkIndex() == null)
                .map(SearchResult::getChunkId)
                .distinct()
                .collect(Collectors.toList());
        if (!missingIndex.isEmpty()) {
            try {
                chunkMapper.selectByIds(missingIndex).forEach(chunk -> {
                    results.stream()
                            .filter(r -> chunk.getId().equals(r.getChunkId()))
                            .forEach(r -> r.setChunkIndex(chunk.getChunkIndex()));
                });
            } catch (Exception e) {
                log.warn("补齐切片序号失败(引用面板退化为仅文件名): {}", e.getMessage());
            }
        }
        for (SearchResult r : results) {
            if (r.getFileId() == null || nameCache.containsKey(r.getFileId())) continue;
            try {
                com.yansheng.aiknowledgebase.entity.FileEntity f = fileMapper.selectById(r.getFileId());
                nameCache.put(r.getFileId(), f != null ? f.getFileName() : null);
            } catch (Exception e) {
                log.warn("填充引用文件名失败, fileId={}", r.getFileId());
                nameCache.put(r.getFileId(), null);
            }
        }
        for (SearchResult r : results) {
            if (r.getFileId() != null) r.setFileName(nameCache.get(r.getFileId()));
        }
    }

    @Override
    public void invalidate(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys("retrieval:" + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已失效用户检索缓存, userId={}, count={}", userId, keys.size());
            }
        } catch (Exception e) {
            // 失效失败不影响主流程:短 TTL 会自动过期兜底
            log.warn("检索缓存失效失败, userId={}, error={}", userId, e.getMessage());
        }
    }

    private String resultCacheKey(String queryText) {
        Long userId = UserContext.getUserId();
        String normalized = queryText == null ? "" : queryText.trim().toLowerCase();
        // 树开关状态并入 key:翻转 tree-boost 后 5min TTL 内不会命中旧排序的缓存
        return "retrieval:" + userId + ":tb=" + (treeBoostEnabled ? "1" : "0") + ":" + md5(normalized);
    }

    private List<SearchResult> readCache(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof List<?> list) {
                return (List<SearchResult>) list;
            }
        } catch (Exception e) {
            // 缓存故障降级为不缓存,直接走全链路检索
            log.warn("检索缓存读取失败, key={}, error={}", key, e.getMessage());
        }
        return null;
    }

    private void writeCache(String key, List<SearchResult> results) {
        try {
            redisTemplate.opsForValue().set(key, results, RESULT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("检索缓存写入失败, key={}, error={}", key, e.getMessage());
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }


}
