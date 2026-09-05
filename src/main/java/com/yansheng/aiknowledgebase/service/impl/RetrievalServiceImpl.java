package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
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
import java.util.LinkedHashMap;
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

    private final VectorSearchService vectorSearchService;
    private final RerankService rerankService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChunkMapper chunkMapper;
    private final com.yansheng.aiknowledgebase.mapper.FileMapper fileMapper;

    @Value("${retrieval.top-k}")
    private int topK;

    @Value("${retrieval.similarity-threshold}")
    private double similarityThreshold;

    @Value("${retrieval.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Value("${retrieval.hybrid.enabled:true}")
    private boolean hybridEnabled;

    public RetrievalServiceImpl(VectorSearchService vectorSearchService,
                                RerankService rerankService,
                                RedisTemplate<String, Object> redisTemplate,
                                ChunkMapper chunkMapper,
                                com.yansheng.aiknowledgebase.mapper.FileMapper fileMapper) {
        this.vectorSearchService = vectorSearchService;
        this.rerankService = rerankService;
        this.redisTemplate = redisTemplate;
        this.chunkMapper = chunkMapper;
        this.fileMapper = fileMapper;
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

        // 4. 重排(精排):粗召回 → 交叉编码器重打分 → 取 topK;失败降级按原分排序
        List<SearchResult> finalResults;
        if (rerankEnabled && !filtered.isEmpty()) {
            finalResults = rerankService.rerank(queryText, filtered, topK);
        } else {
            // 兜底排序(无重排时):保持合并顺序——向量段(DashVector 距离升序)在前,
            // BM25 段(SQL 相关度降序)在后,两段各自天然有序。
            // 不可整体按 score 排序:两路 score 语义相反(距离 vs 相关度),混排必错一路。
            finalResults = filtered.size() > topK
                    ? new ArrayList<>(filtered.subList(0, topK))
                    : filtered;
        }

        fillFileNames(finalResults);
        writeCache(cacheKey, finalResults);
        return finalResults;
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
        return "retrieval:" + userId + ":" + md5(normalized);
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
