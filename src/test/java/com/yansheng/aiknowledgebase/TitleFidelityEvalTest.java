package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.OutlineChunkRefEntity;
import com.yansheng.aiknowledgebase.entity.OutlineNodeEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.OutlineMapper;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.service.OutlineIndexService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * B-124 树节点标题忠实度抽样体检(Eval Harness,只观察不设阈值,沿用 Sprint 8 模式)。
 *
 * 定位(双重价值):
 *  - ① 为 B-123「AI 生成节点」积累验证器数据与阈值依据:先量出现有解析树的自命中分布,再谈阈值。
 *  - ② 对纯解析树做解析链路回归体检:StructureAwareSplitter 改动 / chunk 重切后,
 *       标题↔区间↔chunk 的关联可能失配,本项用固定样本把这种漂移暴露成命中率变化。
 *
 * 口径(复用 {@link RetrievalQualityEvalTest} 的 retrieveTopK() 与 chunk 级命中判定):
 *  - 节点 title 作 query → retrievalService.retrieveTopK(title) → 取 top-{@link #EVAL_TOP_K};
 *  - 「命中」= top-K 中存在结果的 chunkId 落在该节点【锚定 chunkId 集合】内
 *    (锚定集合由 OutlineMapper.selectRefsByFileId 分组得到)。
 *  - 判定定义为「语义检索是否把该节点锚定切片排进前 K」的阈值化语义判定
 *    (检索侧本身已做 similarity-threshold 向量过滤 + BM25 合并 + Rerank 精排),
 *    而非 title 与 chunk 正文的精确字符串匹配;故不属「用尺子量尺子」的精确匹配循环。
 *
 * R2 预案落地:
 *  - 短标题假阳性 → {@link #MIN_TITLE_LENGTH} 最小标题长度过滤(过短标题跳过并计数);
 *  - paraphrase 假阴性 → 命中由检索语义决定(非字符串匹配),并在输出中如实分类统计。
 *
 * 明确局限(必须在结论中声明,勿过度解读绝对数值):
 *  - StructureAwareSplitter 给【每个 chunk】前置所属标题行,title 查询与锚定 chunk 正文首行存在字面重叠,
 *    会经 BM25 路放大自命中,使总体忠实度偏高、偏乐观;
 *  - 因此本项定位为「解析链路自洽体检 + AI 生成节点验证器基线」,不宣称独立语义忠实度。
 *
 * 采样来源:注入一份【固定结构化样例】(多级 H1/H2/H3 + 明确正文段落),走真实管线
 *   createNote(建 chunk+向量) → OutlineIndexService.indexFile(建树+关联),
 *   可重复、确定性强,固定样本同时充当解析链路漂移探针。
 *
 * 不做阈值断言;但设【防静默空跑】健全性断言:待检节点数 > 0,否则判体检无效。
 * 需真实 DashVector/DashScope,默认门禁(excludedGroups=integration,e2e)不跑本类。
 */
@SpringBootTest
@Tag("e2e")
@ActiveProfiles("local")
class TitleFidelityEvalTest {

    /**
     * 固定结构化样例:标题长度刻意跨越三个分桶(<8 / 8-15 / >=16),每节一条明确正文段落。
     * "路由"仅 2 字符,用于验证 R2 最小标题长度过滤确实生效(会被跳过并计数)。
     */
    private static final String SAMPLE_MD = """
            # 知识库检索系统整体设计

            本文档描述检索系统的整体设计、关键链路与核心决策依据。

            ## 向量检索链路设计

            向量检索链路负责把查询文本编码为向量,并在向量库中做近邻搜索与阈值过滤。

            ### 切片策略

            切片策略决定语义单元边界,直接影响检索召回质量与引用溯源的粒度。

            ## 路由

            路由模块负责查询的分流与检索范围收敛。

            ## 混合检索与重排序链路的完整实现说明

            混合检索融合向量与关键词两路结果,再经重排序模型精排得到最终排序。
            """;

    /** 样品笔记标题(无 HTML,保证 createNote 落库文件名 == 该标题) */
    private static final String SAMPLE_TITLE = "title-fidelity-eval-样本";

    /** R2 预案:最小标题长度过滤(过短标题易造成假阳性),单位:字符 */
    private static final int MIN_TITLE_LENGTH = 4;

    /** 命中判定取前 K 条检索结果 */
    private static final int EVAL_TOP_K = 5;

    /** 标题长度分桶边界:小于 BUCKET_MID 归 <8;介于 BUCKET_MID..BUCKET_LONG 归 8-15;其余归 >=16 */
    private static final int BUCKET_MID = 8;
    private static final int BUCKET_LONG = 16;

    /** 向量可见性探测:最多等待秒数;探测 query 取样品内标题,确保能召回本次注入内容 */
    private static final int INDEX_WAIT_SECONDS = 30;
    private static final String PROBE_QUERY = "向量检索链路设计";

    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private RetrievalService retrievalService;
    @Autowired
    private OutlineIndexService outlineIndexService;
    @Autowired
    private OutlineMapper outlineMapper;
    @Autowired
    private FileMapper fileMapper;

    @Test
    void evalTitleFidelity() {
        // 1. 独立测试用户(检索按用户隔离,评估环境只含本次注入样本)
        long ts = System.currentTimeMillis();
        UserEntity user = new UserEntity();
        user.setId(ts);
        user.setUsername("title-fidelity-eval-" + ts);
        UserContext.set(user);

        Long knowledgeId = null;
        try {
            // 2. 注入样本:建知识容器 → createNote 走真实切片+向量化管线
            String containerTitle = "title-fidelity-eval-容器-" + ts;
            KnowledgeAddDTO dto = new KnowledgeAddDTO();
            dto.setTitle(containerTitle);
            dto.setContent("title fidelity eval container");
            dto.setCategory("title-fidelity-eval");
            knowledgeService.addKnowledge(dto);
            knowledgeId = findKnowledgeId(containerTitle);

            knowledgeService.createNote(knowledgeId, SAMPLE_TITLE, SAMPLE_MD, "title-fidelity-eval");
            Long fileId = findFileId(knowledgeId, SAMPLE_TITLE);

            // 3. 建树 + 节点↔chunk 关联(复用生产 OutlineIndexService,关联判据与生产同源)
            int nodeCount = outlineIndexService.indexFile(fileId, SAMPLE_MD);

            // 4. 等待向量可见(注入即索引,探测兜底传播延迟)
            waitUntilIndexed(user.getId());

            // 5. 组装节点→锚定 chunkId 集合
            Map<Long, Set<Long>> anchoredByNode = new LinkedHashMap<>();
            for (OutlineChunkRefEntity ref : outlineMapper.selectRefsByFileId(fileId)) {
                anchoredByNode.computeIfAbsent(ref.getNodeId(), k -> new LinkedHashSet<>()).add(ref.getChunkId());
            }
            List<OutlineNodeEntity> nodes = outlineMapper.selectNodesByFileId(fileId);

            // 6. 逐节点体检
            int candidateCount = 0;
            int hitCount = 0;
            int skippedShort = 0;
            int skippedNoChunk = 0;
            Map<String, int[]> byBucket = new TreeMap<>();
            List<String> misses = new ArrayList<>();

            for (OutlineNodeEntity node : nodes) {
                String title = node.getTitle();
                // R2:最小标题长度过滤(过短标题跳过并计数)
                if (title == null || title.length() < MIN_TITLE_LENGTH) {
                    skippedShort++;
                    continue;
                }
                Set<Long> anchored = anchoredByNode.get(node.getId());
                if (anchored == null || anchored.isEmpty()) {
                    skippedNoChunk++;
                    continue;
                }

                candidateCount++;
                List<SearchResult> results = retrievalService.retrieveTopK(title);
                List<SearchResult> topK = results.subList(0, Math.min(EVAL_TOP_K, results.size()));
                // 命中 = top-K 中存在 chunkId 落于该节点锚定集合内
                boolean hit = topK.stream().anyMatch(r -> r.getChunkId() != null && anchored.contains(r.getChunkId()));

                int[] agg = byBucket.computeIfAbsent(bucketOf(title.length()), k -> new int[2]);
                agg[1]++;
                if (hit) {
                    hitCount++;
                    agg[0]++;
                } else {
                    misses.add(String.format(
                            "fileId=%d | headingPath=%s | title=%s | 锚定chunkId=%s | topK实际chunkId=%s",
                            fileId, node.getHeadingPath(), title, anchored,
                            topK.stream().map(SearchResult::getChunkId).toList()));
                }
            }

            // 7. 忠实度分布报告(只观察,不设阈值)
            System.out.println("\n===== TITLE FIDELITY EVAL 结果(只观察不设阈值)=====");
            System.out.printf("采样:固定结构化样例 | fileId=%d | outline 节点数=%d%n", fileId, nodeCount);
            System.out.printf("待检节点(标题>=%d 且有锚定chunk) = %d | 命中 = %d | 总体忠实度 = %.3f%n",
                    MIN_TITLE_LENGTH, candidateCount, hitCount,
                    candidateCount == 0 ? 0.0 : (double) hitCount / candidateCount);
            System.out.printf("跳过:标题过短 = %d | 无锚定chunk = %d%n", skippedShort, skippedNoChunk);
            System.out.println("--- 按标题长度分桶(命中/待检)---");
            for (Map.Entry<String, int[]> e : byBucket.entrySet()) {
                int[] v = e.getValue();
                System.out.printf("  %-6s hit/total = %d/%d (%.3f)%n",
                        e.getKey(), v[0], v[1], v[1] == 0 ? 0.0 : (double) v[0] / v[1]);
            }
            System.out.println("--- 未命中节点清单(标题↔锚定切片未进 top-K)---");
            for (String m : misses) {
                System.out.println("  " + m);
            }
            System.out.println("==============================================");

            // 8. 防静默空跑:未采样到任何待检节点 = 体检无效(不得假绿)
            if (candidateCount == 0) {
                throw new AssertionError("未采样到任何待检节点,体检无效");
            }

        } finally {
            // 9. 自清理:级联删 chunks/files/vectors + 失效检索缓存(失败也要清)
            try {
                if (knowledgeId != null) {
                    UserContext.set(user);
                    knowledgeService.deleteKnowledge(knowledgeId);
                }
            } catch (Exception e) {
                System.err.println("清理体检数据失败(可跑 cleanup_test_data.py 兜底): " + e.getMessage());
            }
            try {
                retrievalService.invalidate(user.getId());
            } catch (Exception ignored) {
            }
            UserContext.remove();
        }
    }

    /** 标题长度分桶:<8 / 8-15 / >=16 */
    private String bucketOf(int titleLength) {
        if (titleLength < BUCKET_MID) {
            return "<8";
        }
        if (titleLength < BUCKET_LONG) {
            return "8-15";
        }
        return ">=16";
    }

    private Long findKnowledgeId(String title) {
        List<KnowledgeVO> list = knowledgeService.getKnowledgeList();
        return list.stream()
                .filter(v -> title.equals(v.getTitle()))
                .map(KnowledgeVO::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到测试知识容器: " + title));
    }

    private Long findFileId(Long knowledgeId, String fileName) {
        return fileMapper.selectFileByKnowledgeId(knowledgeId).stream()
                .filter(f -> fileName.equals(f.getFileName()))
                .map(FileEntity::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到注入的样本文件: " + fileName));
    }

    /** 向量可见性探测:命中即认为索引就绪;重试前失效缓存防空结果被缓存 */
    private void waitUntilIndexed(Long userId) {
        for (int i = 0; i < INDEX_WAIT_SECONDS; i++) {
            retrievalService.invalidate(userId);
            if (!retrievalService.retrieveTopK(PROBE_QUERY).isEmpty()) {
                System.out.printf("索引就绪(等待 %d 秒)%n", i);
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待向量索引时被中断,体检中止");
            }
        }
        throw new AssertionError("向量索引 " + INDEX_WAIT_SECONDS + " 秒内不可见,体检中止");
    }
}
