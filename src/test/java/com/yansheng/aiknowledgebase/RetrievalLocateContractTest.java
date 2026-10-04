package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.controller.RetrievalController;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.handler.GlobalExceptionHandler;
import com.yansheng.aiknowledgebase.service.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B-110 T-1 只读端点 POST /api/retrieval/locate 契约:
 * 正向字段映射与顺序、空/纯空白 query 拒绝(未触达检索)、超长 query 前移拦截、topK 收敛、响应字段完整性。
 * 契约:HTTP 恒 200,失败 body code=500。默认 surefire 执行(无 @Tag("integration"))。
 */
class RetrievalLocateContractTest {

    private RetrievalService retrievalService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        retrievalService = mock(RetrievalService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RetrievalController(retrievalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static SearchResult hit(Long fileId, Long chunkId, String content, Double score, String fileName, Integer chunkIndex) {
        SearchResult r = new SearchResult(fileId, chunkId, content, score);
        r.setFileName(fileName);
        r.setChunkIndex(chunkIndex);
        return r;
    }

    private static List<SearchResult> hits(int n) {
        List<SearchResult> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(hit((long) (i + 1), (long) (i + 100), "content-" + i, 1.0 - i * 0.01, "file-" + i + ".md", i));
        }
        return list;
    }

    /** 正向命中:VO 各字段逐一映射正确且保持检索原顺序 */
    @Test
    void mapsAllFieldsAndKeepsOrder() throws Exception {
        SearchResult a = hit(11L, 101L, "第一段内容", 0.93, "设计文档.md", 2);
        SearchResult b = hit(22L, 202L, "第二段内容", 0.81, "接口文档.md", 5);
        when(retrievalService.retrieveTopK("alpha-text")).thenReturn(List.of(a, b));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"alpha-text\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fileId").value(11))
                .andExpect(jsonPath("$.data[0].fileName").value("设计文档.md"))
                .andExpect(jsonPath("$.data[0].chunkIndex").value(2))
                .andExpect(jsonPath("$.data[0].content").value("第一段内容"))
                .andExpect(jsonPath("$.data[0].score").value(0.93))
                .andExpect(jsonPath("$.data[1].fileId").value(22))
                .andExpect(jsonPath("$.data[1].fileName").value("接口文档.md"))
                .andExpect(jsonPath("$.data[1].chunkIndex").value(5))
                .andExpect(jsonPath("$.data[1].content").value("第二段内容"))
                .andExpect(jsonPath("$.data[1].score").value(0.81));

        verify(retrievalService).retrieveTopK("alpha-text");
    }

    /** query 去空白后传给检索(前后空白被裁剪) */
    @Test
    void queryIsTrimmedBeforeRetrieval() throws Exception {
        when(retrievalService.retrieveTopK("alpha")).thenReturn(hits(1));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  alpha  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(retrievalService).retrieveTopK("alpha");
    }

    /** 纯空白 query:@NotBlank 在 Web 层拦截,HTTP 200 + body code=500,且不触达检索 */
    @Test
    void blankQueryRejectedBeforeRetrieval() throws Exception {
        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("查询文本不能为空"));

        verify(retrievalService, never()).retrieveTopK(any());
    }

    /** query 缺失:@NotBlank 在 Web 层拦截,HTTP 200 + body code=500,且不触达检索 */
    @Test
    void missingQueryRejectedBeforeRetrieval() throws Exception {
        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("查询文本不能为空"));

        verify(retrievalService, never()).retrieveTopK(any());
    }

    /** 超长 query(2001 字):@Size 前移拦截,code=500、提示过长,且不触达检索(防资源放大) */
    @Test
    void overlongQueryRejectedBeforeRetrieval() throws Exception {
        String tooLong = "a".repeat(2001);

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + tooLong + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(containsString("过长")));

        verify(retrievalService, never()).retrieveTopK(any());
    }

    /** 边界放行:恰好 2000 字通过校验并进入检索 */
    @Test
    void queryAtMaxLengthReachesRetrieval() throws Exception {
        String maxLen = "a".repeat(2000);
        when(retrievalService.retrieveTopK(any())).thenReturn(hits(1));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + maxLen + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(retrievalService).retrieveTopK(any());
    }

    /** topK=99 越上界 → 收敛为 10 条 */
    @Test
    void topKAboveUpperBoundClampedToTen() throws Exception {
        when(retrievalService.retrieveTopK(any())).thenReturn(hits(20));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[9].content").value("content-9"));
    }

    /** topK=0 → 收敛为 1 条 */
    @Test
    void topKZeroClampedToSingle() throws Exception {
        when(retrievalService.retrieveTopK(any())).thenReturn(hits(20));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("content-0"));
    }

    /** topK=-1 → 收敛为 1 条 */
    @Test
    void topKNegativeClampedToSingle() throws Exception {
        when(retrievalService.retrieveTopK(any())).thenReturn(hits(20));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /** topK 缺省 → 默认 5 条 */
    @Test
    void topKMissingDefaultsToFive() throws Exception {
        when(retrievalService.retrieveTopK(any())).thenReturn(hits(20));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[4].content").value("content-4"));
    }

    /** topK 在 (1,10) 内:受 min(topK, size) 约束,不越界补位 */
    @Test
    void topKWithinRangeRespectedAndCappedBySize() throws Exception {
        when(retrievalService.retrieveTopK(any())).thenReturn(hits(3));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    /** 未命中:返回空数组而非 null,前端据此给出「未找到」反馈 */
    @Test
    void emptyResultReturnsEmptyArrayNotNull() throws Exception {
        when(retrievalService.retrieveTopK(any())).thenReturn(List.of());

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** 响应字段完整性:每条命中都带全部 5 个契约字段 */
    @Test
    void everyHitCarriesAllContractFields() throws Exception {
        when(retrievalService.retrieveTopK(any())).thenReturn(hits(2));

        mvc.perform(post("/api/retrieval/locate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"q\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileId").exists())
                .andExpect(jsonPath("$.data[0].fileName").exists())
                .andExpect(jsonPath("$.data[0].chunkIndex").exists())
                .andExpect(jsonPath("$.data[0].content").exists())
                .andExpect(jsonPath("$.data[0].score").exists())
                .andExpect(jsonPath("$.data[1].fileId").exists())
                .andExpect(jsonPath("$.data[1].fileName").exists())
                .andExpect(jsonPath("$.data[1].chunkIndex").exists())
                .andExpect(jsonPath("$.data[1].content").exists())
                .andExpect(jsonPath("$.data[1].score").exists());
    }
}
