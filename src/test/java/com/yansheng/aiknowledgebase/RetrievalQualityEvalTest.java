package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Eval Harness:检索质量评估(recall@K / MRR,数据集驱动)
 *
 * 与 EvalHarnessTest(工具选择评估)互补,补齐"检索端"质量度量:
 *  - 数据集 src/test/resources/eval/retrieval-cases.json:docs 为注入知识库的测试文档,
 *    cases.expectDocs 引用 docs.id —— 加用例只改 JSON,不动代码。
 *  - 数据注入走真实生产管线:createNote(切片 → Embedding → DashVector 入库),
 *    评估对象是混合检索(向量+BM25)+ Rerank 全链路,不是 mock。
 *  - 独立测试用户 + 用例后 deleteKnowledge 级联清理(chunks/files/vectors/cache)。
 *
 * 指标:
 *  - recall@5 / MRR:文档级(按 fileId 去重,口径与 v3 完全一致,阈值不动)
 *  - chunkRecall@5 / chunkMRR:chunk 级(树敏感,命中 chunk 需落在 Top-5 chunk 内),
 *    v4 新增 expectChunks 标注后启用,为 Sprint 9 知识树对命中 chunk 提权提供无树基线对比
 *
 * 面试讲法:工具选择准确率(15/15)只证明"该不该检索"对了,
 * 检索质量 eval 证明"检索回来的是什么"也对——评估体系两端闭环。
 */
@SpringBootTest
@Tag("e2e")
@ActiveProfiles("local")
class RetrievalQualityEvalTest {

    private static final String CASES_FILE = "eval/retrieval-cases.json";

    /** 阈值:recall@5 与 MRR 的回归下限(v2 数据集 10 篇文档实测基线 1.0/1.0,留方差余量防静默退化) */
    private static final double RECALL_THRESHOLD = 0.80;
    private static final double MRR_THRESHOLD = 0.70;

    /**
     * chunk 级(树敏感)阈值:与文档级并存,度量命中 chunk 是否落在 Top-5。
     * 实测无树基线(2026-10-03,两次复跑逐位一致):文档级 0.833/0.861、chunk 级 chunkRecall@5=0.833 / chunkMRR=0.861。
     * B-117 修复后向量路已生效(实测检索日志「向量 15 条 + BM25 N 条」);但本数据集上该指标与 BM25 单路基线完全相同,
     * 即当前无树基线,作为 Sprint 9 有树对比的基准;阈值 0.75/0.75 相对 0.833/0.861 留方差余量 0.08/0.11,防静默退化。
     */
    private static final double CHUNK_RECALL_THRESHOLD = 0.75;
    private static final double CHUNK_MRR_THRESHOLD = 0.75;

    /** 向量写入后的可见性探测:最多等这么久(秒) */
    private static final int INDEX_WAIT_SECONDS = 30;

    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private RetrievalService retrievalService;
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void evalRetrievalQuality() throws Exception {
        // 1. 读取数据集
        EvalDataset dataset;
        try (InputStream in = new ClassPathResource(CASES_FILE).getInputStream()) {
            dataset = objectMapper.readValue(in, EvalDataset.class);
        }
        Map<String, String> meta = dataset.meta;

        // 2. 独立测试用户(检索按用户隔离,评估环境只含本次注入的文档)
        long ts = System.currentTimeMillis();
        UserEntity user = new UserEntity();
        user.setId(ts);
        user.setUsername("retrieval-eval-" + ts);
        UserContext.set(user);

        Long knowledgeId = null;
        try {
            // 3. 注入:建知识容器 → 逐篇 createNote(真实切片+向量化管线)
            String containerTitle = "retrieval-eval-容器-" + ts;
            KnowledgeAddDTO dto = new KnowledgeAddDTO();
            dto.setTitle(containerTitle);
            dto.setContent("retrieval quality eval container");
            dto.setCategory("retrieval-eval");
            knowledgeService.addKnowledge(dto);
            knowledgeId = findKnowledgeId(containerTitle);

            for (EvalDoc doc : dataset.docs) {
                knowledgeService.createNote(knowledgeId, doc.title, doc.content, null);
            }

            // doc.id → note fileId(按文件名对齐),及反查表用于可读输出
            Map<String, Long> docFileIds = new LinkedHashMap<>();
            Map<Long, String> fileNames = new LinkedHashMap<>();
            for (FileEntity f : fileMapper.selectFileByKnowledgeId(knowledgeId)) {
                fileNames.put(f.getId(), f.getFileName());
            }
            for (EvalDoc doc : dataset.docs) {
                Long fileId = fileNames.entrySet().stream()
                        .filter(e -> doc.title.equals(e.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("未找到注入的笔记文件: " + doc.title));
                docFileIds.put(doc.id, fileId);
            }

            // 4. 等待向量可见(DashVector 写入通常立即可读,探测兜底传播延迟)
            waitUntilIndexed(user.getId());

            // 5. 逐用例评估:文档级排名 → recall@5 / MRR;chunk 级(树敏感)→ chunkRecall@5 / chunkMRR
            int total = dataset.cases.size();
            double recallSum = 0;
            double mrrSum = 0;
            Map<String, double[]> byTask = new TreeMap<>();
            List<String> failures = new ArrayList<>();
            // chunk 级累加器(仅统计带 expectChunks 的用例;文档级累加器与口径完全不动)
            int chunkCaseCount = 0;
            double chunkRecallSum = 0;
            double chunkMrrSum = 0;
            Map<String, double[]> chunkByTask = new TreeMap<>();
            List<String> chunkFailures = new ArrayList<>();

            for (EvalCase c : dataset.cases) {
                // 保留原始检索结果(chunkId/content/fileId/fileName/chunkIndex 及返回顺序),文档级与 chunk 级共用同一次检索
                List<SearchResult> results = retrievalService.retrieveTopK(c.query);
                List<Long> ranked = rankedDocIds(results);

                List<Long> expected = c.expectDocs.stream().map(docFileIds::get).toList();
                List<Long> top5 = ranked.subList(0, Math.min(5, ranked.size()));
                long hits = expected.stream().filter(top5::contains).count();
                double recall = expected.isEmpty() ? 0 : (double) hits / expected.size();

                int firstRank = 0;
                for (int i = 0; i < ranked.size(); i++) {
                    if (expected.contains(ranked.get(i))) {
                        firstRank = i + 1;
                        break;
                    }
                }
                double rr = firstRank == 0 ? 0 : 1.0 / firstRank;

                recallSum += recall;
                mrrSum += rr;
                double[] agg = byTask.computeIfAbsent(c.task, t -> new double[3]);
                agg[0] += recall;
                agg[1] += rr;
                agg[2] += 1;

                if (recall < 1.0 || firstRank == 0) {
                    failures.add(String.format("❌ [%s/%s] %s → 预期=%s 实际Top5=%s",
                            c.id, c.task, c.query,
                            c.expectDocs,
                            top5.stream().map(id -> docIdOf(docFileIds, id)).toList()));
                }

                // chunk 级:对同一批原始 Top-5 chunk 判定期望命中(文档 fileId + content 关键词双条件)
                if (!c.expectChunks.isEmpty()) {
                    List<SearchResult> top5Chunks = results.subList(0, Math.min(5, results.size()));

                    // chunkRecall@5 = Top-5 chunk 中被满足的期望数 / 期望总数
                    int hitExpect = 0;
                    for (ExpectChunk ec : c.expectChunks) {
                        Long fid = docFileIds.get(ec.doc);
                        boolean ok = top5Chunks.stream().anyMatch(r ->
                                Objects.equals(r.getFileId(), fid) && containsAny(r.getContent(), ec.any));
                        if (ok) {
                            hitExpect++;
                        }
                    }
                    double cRecall = (double) hitExpect / c.expectChunks.size();

                    // chunkMRR = 首条满足任一期望的 chunk 排名的倒数(未命中记 0)
                    int firstChunkRank = 0;
                    for (int i = 0; i < results.size(); i++) {
                        SearchResult r = results.get(i);
                        boolean ok = false;
                        for (ExpectChunk ec : c.expectChunks) {
                            if (Objects.equals(r.getFileId(), docFileIds.get(ec.doc))
                                    && containsAny(r.getContent(), ec.any)) {
                                ok = true;
                                break;
                            }
                        }
                        if (ok) {
                            firstChunkRank = i + 1;
                            break;
                        }
                    }
                    double cMrr = firstChunkRank == 0 ? 0 : 1.0 / firstChunkRank;

                    chunkCaseCount++;
                    chunkRecallSum += cRecall;
                    chunkMrrSum += cMrr;
                    double[] cAgg = chunkByTask.computeIfAbsent(c.task, t -> new double[3]);
                    cAgg[0] += cRecall;
                    cAgg[1] += cMrr;
                    cAgg[2] += 1;

                    if (cRecall < 1.0 || firstChunkRank == 0) {
                        String expectDesc = c.expectChunks.stream()
                                .map(ec -> ec.doc + ec.any).toList().toString();
                        List<String> actualDesc = new ArrayList<>();
                        for (SearchResult r : top5Chunks) {
                            List<String> hitWords = new ArrayList<>();
                            for (ExpectChunk ec : c.expectChunks) {
                                if (Objects.equals(r.getFileId(), docFileIds.get(ec.doc)) && r.getContent() != null) {
                                    for (String w : ec.any) {
                                        if (r.getContent().contains(w)) {
                                            hitWords.add(w);
                                        }
                                    }
                                }
                            }
                            actualDesc.add(r.getFileName() + ":" + r.getChunkIndex()
                                    + (hitWords.isEmpty() ? "" : "[" + String.join(",", hitWords) + "]"));
                        }
                        chunkFailures.add(String.format("❌ [%s/%s] %s → 期望=%s 实际Top5=%s",
                                c.id, c.task, c.query, expectDesc, actualDesc));
                    }
                }
            }

            // 6. 分层报告
            double recall = recallSum / total;
            double mrr = mrrSum / total;
            double chunkRecall = chunkCaseCount == 0 ? 0 : chunkRecallSum / chunkCaseCount;
            double chunkMrr = chunkCaseCount == 0 ? 0 : chunkMrrSum / chunkCaseCount;
            System.out.println("\n===== RETRIEVAL EVAL 结果 =====");
            System.out.printf("数据集: %s v%s | 管线: %s | 用例数: %d%n",
                    meta.getOrDefault("name", "-"), meta.getOrDefault("version", "-"),
                    meta.getOrDefault("pipeline", "-"), total);
            System.out.printf("总体 recall@5 = %.3f | MRR = %.3f%n", recall, mrr);
            System.out.println("--- 按任务类型(召回/排序短板一眼可见) ---");
            for (Map.Entry<String, double[]> e : byTask.entrySet()) {
                double[] v = e.getValue();
                System.out.printf("  %-10s recall@5=%.3f MRR=%.3f (%.0f例)%n",
                        e.getKey(), v[0] / v[2], v[1] / v[2], v[2]);
            }
            System.out.println("--- 未达满分的案例(进缺陷清单,优化切片/混合权重后重跑) ---");
            for (String f : failures) {
                System.out.println("  " + f);
            }
            System.out.println("--- chunk 级(树敏感,Top-5 chunk)---");
            System.out.printf("总体 chunkRecall@5 = %.3f | chunkMRR = %.3f (含期望用例 %d 条)%n",
                    chunkRecall, chunkMrr, chunkCaseCount);
            System.out.println("--- chunk 级按任务类型 ---");
            for (Map.Entry<String, double[]> e : chunkByTask.entrySet()) {
                double[] v = e.getValue();
                System.out.printf("  %-14s chunkRecall@5=%.3f chunkMRR=%.3f (%.0f例)%n",
                        e.getKey(), v[0] / v[2], v[1] / v[2], v[2]);
            }
            System.out.println("--- chunk 级未命中案例(query / 期望 doc+关键词 / 实际 Top-5 fileName:chunkIndex[命中词]) ---");
            for (String f : chunkFailures) {
                System.out.println("  " + f);
            }
            System.out.println("==============================");

            // 7. 断言:低于阈值 = 检索链路退化,需排查(而非必须满分)
            org.junit.jupiter.api.Assertions.assertTrue(recall >= RECALL_THRESHOLD,
                    String.format("recall@5 = %.3f 低于阈值 %.2f,检索召回退化,需排查混合检索/切片策略", recall, RECALL_THRESHOLD));
            org.junit.jupiter.api.Assertions.assertTrue(mrr >= MRR_THRESHOLD,
                    String.format("MRR = %.3f 低于阈值 %.2f,检索排序退化,需排查 Rerank 链路", mrr, MRR_THRESHOLD));
            org.junit.jupiter.api.Assertions.assertTrue(chunkRecall >= CHUNK_RECALL_THRESHOLD,
                    String.format("chunkRecall@5 = %.3f 低于阈值 %.2f,chunk 级召回/排序退化,Sprint 9 有树对比基准需复核", chunkRecall, CHUNK_RECALL_THRESHOLD));
            org.junit.jupiter.api.Assertions.assertTrue(chunkMrr >= CHUNK_MRR_THRESHOLD,
                    String.format("chunkMRR = %.3f 低于阈值 %.2f,chunk 级召回/排序退化,Sprint 9 有树对比基准需复核", chunkMrr, CHUNK_MRR_THRESHOLD));

        } finally {
            // 8. 自清理:级联删 chunks/files/vectors + 失效检索缓存(失败也要清)
            try {
                if (knowledgeId != null) {
                    UserContext.set(user);
                    knowledgeService.deleteKnowledge(knowledgeId);
                }
            } catch (Exception e) {
                System.err.println("清理评估数据失败(可跑 cleanup_test_data.py 兜底): " + e.getMessage());
            }
            try {
                retrievalService.invalidate(user.getId());
            } catch (Exception ignored) {
            }
            UserContext.remove();
        }
    }

    /** 文档级排名(按首个出现顺序去重);检索结果由调用方保留复用,去重算法与 v3 完全一致 */
    private List<Long> rankedDocIds(List<SearchResult> results) {
        List<Long> ranked = new ArrayList<>();
        for (SearchResult r : results) {
            if (r.getFileId() != null && !ranked.contains(r.getFileId())) {
                ranked.add(r.getFileId());
            }
        }
        return ranked;
    }

    /** chunk 命中判定:content 含期望关键词之一即算命中 */
    private boolean containsAny(String content, List<String> words) {
        if (content == null) {
            return false;
        }
        for (String w : words) {
            if (content.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 向量可见性探测:命中任一结果即认为索引就绪;失败重试前失效缓存防空结果被缓存 */
    private void waitUntilIndexed(Long userId) throws InterruptedException {
        for (int i = 0; i < INDEX_WAIT_SECONDS; i++) {
            retrievalService.invalidate(userId);
            if (!retrievalService.retrieveTopK("垃圾回收器 ZGC 停顿").isEmpty()) {
                System.out.printf("索引就绪(等待 %d 秒)%n", i);
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("向量索引 " + INDEX_WAIT_SECONDS + " 秒内不可见,评估中止");
    }

    private Long findKnowledgeId(String title) {
        List<KnowledgeVO> list = knowledgeService.getKnowledgeList();
        return list.stream()
                .filter(v -> title.equals(v.getTitle()))
                .map(KnowledgeVO::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到测试知识容器: " + title));
    }

    private String docIdOf(Map<String, Long> docFileIds, Long fileId) {
        return docFileIds.entrySet().stream()
                .filter(e -> fileId.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("unknown");
    }

    /** 数据集结构(与 retrieval-cases.json 对应) */
    static class EvalDataset {
        public Map<String, String> meta = new LinkedHashMap<>();
        public List<EvalDoc> docs = new ArrayList<>();
        public List<EvalCase> cases = new ArrayList<>();
    }

    static class EvalDoc {
        public String id;
        public String title;
        public String content;
    }

    static class EvalCase {
        public String id;
        public String task;
        public String difficulty;
        public String query;
        public List<String> expectDocs = new ArrayList<>();
        /** v4 新增:chunk 级期望(缺省空列表 → 该用例跳过 chunk 级统计) */
        public List<ExpectChunk> expectChunks = new ArrayList<>();
    }

    /** chunk 级期望:命中 ⟺ chunk 属于 doc 对应文件且 content 含 any 中至少一个词 */
    static class ExpectChunk {
        public String doc;
        public List<String> any = new ArrayList<>();
    }
}
