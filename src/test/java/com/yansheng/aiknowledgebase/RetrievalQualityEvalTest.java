package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.entity.FileEntity;
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
 *  - recall@5:期望文档出现在 Top-5 的比例(文档级,按首个命中 rank 去重)
 *  - MRR:首个相关文档排名的倒数均值(排序质量)
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

            // 5. 逐用例评估:文档级排名 → recall@5 / MRR,按任务类型分层
            int total = dataset.cases.size();
            double recallSum = 0;
            double mrrSum = 0;
            Map<String, double[]> byTask = new TreeMap<>();
            List<String> failures = new ArrayList<>();

            for (EvalCase c : dataset.cases) {
                List<Long> ranked = rankedDocIds(c.query);

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
            }

            // 6. 分层报告
            double recall = recallSum / total;
            double mrr = mrrSum / total;
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
            System.out.println("==============================");

            // 7. 断言:低于阈值 = 检索链路退化,需排查(而非必须满分)
            org.junit.jupiter.api.Assertions.assertTrue(recall >= RECALL_THRESHOLD,
                    String.format("recall@5 = %.3f 低于阈值 %.2f,检索召回退化,需排查混合检索/切片策略", recall, RECALL_THRESHOLD));
            org.junit.jupiter.api.Assertions.assertTrue(mrr >= MRR_THRESHOLD,
                    String.format("MRR = %.3f 低于阈值 %.2f,检索排序退化,需排查 Rerank 链路", mrr, MRR_THRESHOLD));

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

    /** 检索一次,返回文档级排名(按首个出现顺序去重) */
    private List<Long> rankedDocIds(String query) {
        List<Long> ranked = new ArrayList<>();
        for (var r : retrievalService.retrieveTopK(query)) {
            if (r.getFileId() != null && !ranked.contains(r.getFileId())) {
                ranked.add(r.getFileId());
            }
        }
        return ranked;
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
    }
}
