package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.service.OutlineIndexService;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
 * 指标(v6):
 *  - 文档级 recall@5 / MRR、chunk 级 chunkRecall@5 / chunkMRR;均按 format(md / pdf)分组输出。
 *  - 分组理由:Sprint 9 树路由加权只对含 markdown 标题结构的 md 文档生效;
 *    无结构文档(如 pdf)建不出 outline 树 → 无树加成。混算会稀释树路由效果,必须分开看。
 *  - md 组沿用 v5 无树基线阈值(0.80/0.70/0.75/0.75);pdf 组为新用例、无历史基线,
 *    只输出不设阻断阈值(先观察后定阈值,用于判断"无结构文档是否为检索短板")。
 *  - 开关:retrieval.tree-boost.enabled 默认 false;可用 -Dretrieval.tree-boost.enabled=true
 *    在本次运行开启树路由(Spring @Value 会读系统属性),用于有树/无树两跑对比。
 *
 * 【如实说明】数据集经 createNote 注入(非真实上传),故 outline 树不自动生成:
 *  - md 用例:注入后由本测试手工调用 outlineIndexService.indexFile(fileId, content) 补建树,
 *    以对齐生产「上传 .md → handleDocument → indexOutlineIfMarkdown → indexFile」的树状态;
 *  - pdf 用例:content 不含 markdown 标题,是「无结构文本」代理,并非真实 pdf 文件解析结果;
 *    其 indexFile 返回 0 节点(无树),与生产非 md 不建树的终态一致。
 *  开关 retrieval.tree-boost.enabled 默认 false → 关闭时建不建树都不改变检索结果,
 *  「关闭 = Sprint 8 无树基线逐位一致」的 DoD 不受本建树前置影响。
 *
 * 面试讲法:工具选择准确率(15/15)只证明"该不该检索"对了,
 * 检索质量 eval 证明"检索回来的是什么"也对——评估体系两端闭环。
 */
@SpringBootTest
@Tag("e2e")
@ActiveProfiles("local")
class RetrievalQualityEvalTest {

    private static final String CASES_FILE = "eval/retrieval-cases.json";

    /** 阈值:recall@5 与 MRR 的回归下限(md 组;沿用 v5 无树基线,留方差余量防静默退化) */
    private static final double RECALL_THRESHOLD = 0.80;
    private static final double MRR_THRESHOLD = 0.70;

    /**
     * chunk 级(树敏感)阈值(md 组):与文档级并存,度量命中 chunk 是否落在 Top-5。
     * 实测无树基线(2026-10-03,两次复跑逐位一致):文档级 0.833/0.861、chunk 级 chunkRecall@5=0.833 / chunkMRR=0.861。
     * 阈值 0.75/0.75 相对 0.833/0.861 留方差余量 0.08/0.11,防静默退化。
     * pdf 组为新用例、无历史基线 → 只输出不设阈值(先观察后定阈值)。
     */
    private static final double CHUNK_RECALL_THRESHOLD = 0.75;
    private static final double CHUNK_MRR_THRESHOLD = 0.75;

    /** 向量写入后的可见性探测:最多等这么久(秒) */
    private static final int INDEX_WAIT_SECONDS = 30;

    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private RetrievalService retrievalService;
    /** B-114 Phase2:手工为 md 用例补建 outline 树,对齐生产「上传 .md」的建树入口 */
    @Autowired
    private OutlineIndexService outlineIndexService;
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private ObjectMapper objectMapper;

    /** 树路由加权开关(默认 false;可在命令行用 -Dretrieval.tree-boost.enabled=true 开启,用于有树/无树两跑) */
    @Value("${retrieval.tree-boost.enabled:false}")
    private boolean treeBoostEnabled;

    @Test
    void evalRetrievalQuality() throws Exception {
        // 1. 读取数据集
        EvalDataset dataset;
        try (InputStream in = new ClassPathResource(CASES_FILE).getInputStream()) {
            dataset = objectMapper.readValue(in, EvalDataset.class);
        }
        Map<String, String> meta = dataset.meta;

        // 1.1 docId → format(md / pdf),用于按格式分组统计
        Map<String, String> docFormats = new LinkedHashMap<>();
        for (EvalDoc doc : dataset.docs) {
            docFormats.put(doc.id, doc.format == null ? "md" : doc.format);
        }

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

            // 3.1 B-114 Phase2 eval 建树前置(补 T-3 缺口):
            //   eval 经 createNote 注入(非真实上传),默认不建 outline 树 → 树路由对 md 也无从生效。
            //   为使 md 用例的树状态与生产「上传 .md → handleDocument → indexOutlineIfMarkdown → indexFile」一致,
            //   这里对每个用例手工调用 indexFile(fileId, content) 补建树(幂等:先删后插)。
            //   pdf 用例是「无结构文本」代理(非真实 pdf,content 无 markdown 标题):同样调用 indexFile,
            //   parser 无标题时返回 0 节点(见 OutlineIndexServiceImpl L77-80),代表「无树」——
            //   与生产非 md 文件不建树的终态一致,故两格式走同一入口、口径一致可比。
            //   注:开关默认关闭时 boost 被 gate 掉,建不建树都不改变检索结果
            //   → 「关闭 = Sprint 8 无树基线逐位一致」这条 DoD 仍成立。
            for (EvalDoc doc : dataset.docs) {
                int nodeCount = outlineIndexService.indexFile(docFileIds.get(doc.id), doc.content);
                System.out.printf("建树 format=%s doc=%s nodeCount=%d%n",
                        doc.format == null ? "md" : doc.format, doc.id, nodeCount);
            }

            // 4. 等待向量可见(DashVector 写入通常立即可读,探测兜底传播延迟)
            waitUntilIndexed(user.getId());

            // 5. 逐用例评估:文档级排名 → recall@5 / MRR;chunk 级(树敏感)→ chunkRecall@5 / chunkMRR
            //    累加器按 format 分组:[recallSum 或 chunkRecallSum, mrrSum 或 chunkMrrSum, count]
            int total = dataset.cases.size();
            double recallSum = 0;
            double mrrSum = 0;
            Map<String, double[]> byFormat = new TreeMap<>();
            List<String> failures = new ArrayList<>();
            int chunkCaseCount = 0;
            double chunkRecallSum = 0;
            double chunkMrrSum = 0;
            Map<String, double[]> chunkByFormat = new TreeMap<>();
            List<String> chunkFailures = new ArrayList<>();

            for (EvalCase c : dataset.cases) {
                // 用例所属 format:expectDocs 的 format 一致时归该 format,否则记入 mixed(不参与阈值)
                Set<String> caseFormats = c.expectDocs.stream()
                        .map(d -> docFormats.getOrDefault(d, "md"))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                String caseFormat = caseFormats.size() == 1 ? caseFormats.iterator().next() : "mixed";

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
                double[] agg = byFormat.computeIfAbsent(caseFormat, t -> new double[3]);
                agg[0] += recall;
                agg[1] += rr;
                agg[2] += 1;

                if (recall < 1.0 || firstRank == 0) {
                    failures.add(String.format("❌ [%s/%s/%s] %s → 预期=%s 实际Top5=%s",
                            c.id, c.task, caseFormat, c.query,
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
                    double[] cAgg = chunkByFormat.computeIfAbsent(caseFormat, t -> new double[3]);
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
                        chunkFailures.add(String.format("❌ [%s/%s/%s] %s → 期望=%s 实际Top5=%s",
                                c.id, c.task, caseFormat, c.query, expectDesc, actualDesc));
                    }
                }
            }

            // 6. 分层报告(总体 + 按 format 分组)
            double recall = recallSum / total;
            double mrr = mrrSum / total;
            double chunkRecall = chunkCaseCount == 0 ? 0 : chunkRecallSum / chunkCaseCount;
            double chunkMrr = chunkCaseCount == 0 ? 0 : chunkMrrSum / chunkCaseCount;
            System.out.println("\n===== RETRIEVAL EVAL 结果 =====");
            System.out.printf("数据集: %s v%s | 管线: %s | 用例数: %d%n",
                    meta.getOrDefault("name", "-"), meta.getOrDefault("version", "-"),
                    meta.getOrDefault("pipeline", "-"), total);
            System.out.printf("树路由加权(tree-boost.enabled) = %s%n", treeBoostEnabled);
            System.out.printf("总体 recall@5 = %.3f | MRR = %.3f | chunkRecall@5 = %.3f | chunkMRR = %.3f%n",
                    recall, mrr, chunkRecall, chunkMrr);
            System.out.println("--- 按 format 分组(md: 树路由可生效 / pdf: 无结构文本代理,无树加成) ---");
            System.out.printf("  %-7s %-14s %-8s %-16s %-11s%n",
                    "format", "recall@5", "MRR", "chunkRecall@5", "chunkMRR");
            for (Map.Entry<String, double[]> e : byFormat.entrySet()) {
                String fmt = e.getKey();
                double[] v = e.getValue();
                double[] cv = chunkByFormat.get(fmt);
                double fmtChunkRecall = (cv == null || cv[2] == 0) ? 0 : cv[0] / cv[2];
                double fmtChunkMrr = (cv == null || cv[2] == 0) ? 0 : cv[1] / cv[2];
                System.out.printf("  %-7s %-14.3f %-8.3f %-16.3f %-11.3f (文档级%.0f例 / chunk级%.0f例) %s%n",
                        fmt, v[0] / v[2], v[1] / v[2], fmtChunkRecall, fmtChunkMrr, v[2],
                        cv == null ? 0 : cv[2],
                        "md" .equals(fmt) ? "[阈值 0.80/0.70/0.75/0.75]"
                                : "pdf".equals(fmt) ? "[观察中,未设阈值]" : "[mixed,未设阈值]");
            }
            System.out.println("--- 文档级未达满分的案例(进缺陷清单,优化切片/混合权重后重跑) ---");
            for (String f : failures) {
                System.out.println("  " + f);
            }
            System.out.println("--- chunk 级未命中案例(query / 期望 doc+关键词 / 实际 Top-5 fileName:chunkIndex[命中词]) ---");
            for (String f : chunkFailures) {
                System.out.println("  " + f);
            }
            System.out.println("==============================");

            // 7. 断言:md 组沿用原阈值;pdf 组只输出不设阻断阈值(先观察后定阈值)
            double[] mdDoc = byFormat.get("md");
            if (mdDoc != null && mdDoc[2] > 0) {
                double mdRecall = mdDoc[0] / mdDoc[2];
                double mdMrr = mdDoc[1] / mdDoc[2];
                org.junit.jupiter.api.Assertions.assertTrue(mdRecall >= RECALL_THRESHOLD,
                        String.format("md 组 recall@5 = %.3f 低于阈值 %.2f,检索召回退化,需排查混合检索/切片策略", mdRecall, RECALL_THRESHOLD));
                org.junit.jupiter.api.Assertions.assertTrue(mdMrr >= MRR_THRESHOLD,
                        String.format("md 组 MRR = %.3f 低于阈值 %.2f,检索排序退化,需排查 Rerank 链路", mdMrr, MRR_THRESHOLD));
            }
            double[] mdChunk = chunkByFormat.get("md");
            if (mdChunk != null && mdChunk[2] > 0) {
                double mdChunkRecall = mdChunk[0] / mdChunk[2];
                double mdChunkMrr = mdChunk[1] / mdChunk[2];
                org.junit.jupiter.api.Assertions.assertTrue(mdChunkRecall >= CHUNK_RECALL_THRESHOLD,
                        String.format("md 组 chunkRecall@5 = %.3f 低于阈值 %.2f,chunk 级召回/排序退化,有树对比基准需复核", mdChunkRecall, CHUNK_RECALL_THRESHOLD));
                org.junit.jupiter.api.Assertions.assertTrue(mdChunkMrr >= CHUNK_MRR_THRESHOLD,
                        String.format("md 组 chunkMRR = %.3f 低于阈值 %.2f,chunk 级召回/排序退化,有树对比基准需复核", mdChunkMrr, CHUNK_MRR_THRESHOLD));
            }
            // pdf 组:无历史基线,只输出观察值,不阻断(见上方分组报告)
            if (byFormat.containsKey("pdf")) {
                double[] pdfDoc = byFormat.get("pdf");
                double[] pdfChunk = chunkByFormat.get("pdf");
                System.out.printf("[观察] pdf 组 recall@5=%.3f MRR=%.3f chunkRecall@5=%.3f chunkMRR=%.3f —— 未设阈值%n",
                        pdfDoc[0] / pdfDoc[2], pdfDoc[1] / pdfDoc[2],
                        (pdfChunk == null || pdfChunk[2] == 0) ? 0 : pdfChunk[0] / pdfChunk[2],
                        (pdfChunk == null || pdfChunk[2] == 0) ? 0 : pdfChunk[1] / pdfChunk[2]);
            }

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
        /** v6 新增:文档格式(md / pdf);缺省视为 md(向后兼容旧数据集) */
        public String format = "md";
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
