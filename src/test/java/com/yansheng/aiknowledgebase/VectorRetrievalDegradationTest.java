package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.mcp.KnowledgeMcpTools;
import com.yansheng.aiknowledgebase.service.RerankService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.service.impl.FileSearchServiceImpl;
import com.yansheng.aiknowledgebase.service.impl.RetrievalServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商故障降级:向量库(DashVector)不可用(额度过期/网络异常)时,
 * 主链路退化为 BM25 单路、工具链路优雅返回空结果——问答与 Agent 不中断、不 500。
 */
class VectorRetrievalDegradationTest {

    private VectorSearchService vectorSearchService;
    private RerankService rerankService;
    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations;
    private ChunkMapper chunkMapper;
    private FileMapper fileMapper;
    private RetrievalServiceImpl retrievalService;
    private FileSearchServiceImpl fileSearchTool;
    private KnowledgeMcpTools mcpTools;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        vectorSearchService = mock(VectorSearchService.class);
        rerankService = mock(RerankService.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        chunkMapper = mock(ChunkMapper.class);
        fileMapper = mock(FileMapper.class);
        KnowledgeMapper knowledgeMapper = mock(KnowledgeMapper.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // 缓存一律未命中,走真实检索

        retrievalService = new RetrievalServiceImpl(vectorSearchService, rerankService, redisTemplate, chunkMapper, fileMapper);
        ReflectionTestUtils.setField(retrievalService, "topK", 3);
        ReflectionTestUtils.setField(retrievalService, "similarityThreshold", 0.35);
        ReflectionTestUtils.setField(retrievalService, "rerankEnabled", false);
        ReflectionTestUtils.setField(retrievalService, "hybridEnabled", true);

        fileSearchTool = new FileSearchServiceImpl(vectorSearchService, new ObjectMapper());
        mcpTools = new KnowledgeMcpTools(vectorSearchService, knowledgeMapper, fileMapper, chunkMapper, new ObjectMapper());

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("degrade_u7");
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private void makeVectorSearchFail() {
        when(vectorSearchService.searchForUser(anyString(), anyInt(), eq(7L)))
                .thenThrow(new RuntimeException("DashVector 实例已过期(模拟)"));
        when(vectorSearchService.search(anyString(), anyInt()))
                .thenThrow(new RuntimeException("DashVector 实例已过期(模拟)"));
    }

    @Test
    void shouldDegradeToBm25WhenVectorSearchFails() {
        // 向量库抛异常 → 不向上传播,自动退化为 BM25 单路,问答继续可用
        makeVectorSearchFail();
        when(chunkMapper.selectByFullText(eq(7L), anyString(), anyInt()))
                .thenReturn(List.of(
                        new SearchResult(1L, 11L, "BM25精确匹配内容", 12.0), // (fileId, chunkId, content, score)
                        new SearchResult(2L, 12L, "BM25第二段", 9.5)));

        List<SearchResult> result = retrievalService.retrieveTopK("降级测试查询");

        // 断言成员而非顺序:BM25 score 语义与向量距离相反,排序由 rerank 层负责(本测试 rerank 关闭)
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getChunkId() == 11L));
        assertTrue(result.stream().anyMatch(r -> r.getChunkId() == 12L));
        verify(chunkMapper).selectByFullText(eq(7L), eq("降级测试查询"), anyInt());
    }

    @Test
    void shouldReturnEmptyWhenVectorFailsAndBm25AlsoEmpty() {
        // 双路全空 → 返回空列表,下游靠"检索为空 Prompt 条件检查"回答"未找到相关内容",绝不硬编内容
        makeVectorSearchFail();
        when(chunkMapper.selectByFullText(eq(7L), anyString(), anyInt()))
                .thenReturn(List.of());

        List<SearchResult> result = retrievalService.retrieveTopK("降级测试查询");

        assertTrue(result.isEmpty());
        verify(chunkMapper).selectByFullText(eq(7L), eq("降级测试查询"), anyInt());
    }

    @Test
    void fileSearchToolShouldReturnEmptyResultsInsteadOfThrowing() {
        // Agent 工具降级:向量失败时返回空 results 的 JSON,ReAct 循环据实回答"未检索到"
        makeVectorSearchFail();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "降级测试查询");

        String json = fileSearchTool.execute(params);

        assertTrue(json.contains("results"));
        assertTrue(json.contains("[]"), "应为空数组,实际: " + json);
        assertFalse(json.isBlank());
    }

    @Test
    void mcpKnowledgeSearchShouldReturnEmptyResultsInsteadOfThrowing() {
        // MCP 出口降级:外部 MCP 客户端调 knowledge_search 不因向量库故障收到 500
        makeVectorSearchFail();

        String json = mcpTools.knowledge_search("降级测试查询");

        assertTrue(json.contains("results"));
        assertTrue(json.contains("[]"), "应为空数组,实际: " + json);
    }
}
