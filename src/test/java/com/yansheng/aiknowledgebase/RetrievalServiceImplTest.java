package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.service.RerankService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.service.impl.RetrievalServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetrievalServiceImplTest {

    private VectorSearchService vectorSearchService;
    private RerankService rerankService;
    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations;
    private ChunkMapper chunkMapper;
    private FileMapper fileMapper;
    private RetrievalServiceImpl retrievalService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        vectorSearchService = mock(VectorSearchService.class);
        rerankService = mock(RerankService.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        chunkMapper = mock(ChunkMapper.class);
        fileMapper = mock(FileMapper.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        retrievalService = new RetrievalServiceImpl(vectorSearchService, rerankService, redisTemplate, chunkMapper, fileMapper);
        // 因为 @Value 注入的字段在纯 new 出来的对象里不会自动赋值，手动塞进去
        ReflectionTestUtils.setField(retrievalService, "topK", 3);
        ReflectionTestUtils.setField(retrievalService, "similarityThreshold", 0.35);
        ReflectionTestUtils.setField(retrievalService, "rerankEnabled", false);
        ReflectionTestUtils.setField(retrievalService, "hybridEnabled", true);
    }

    @Test
    void shouldFilterOutResultsAboveThreshold() {
        // 构造 5 条候选结果，score 有高有低，且故意打乱顺序
        List<SearchResult> mockResults = Arrays.asList(
                new SearchResult(1L, 1L, "内容A", 0.10), // 相关
                new SearchResult(2L, 2L, "内容B", 0.50), // 不相关，应被过滤
                new SearchResult(3L, 3L, "内容C", 0.20), // 相关
                new SearchResult(4L, 4L, "内容D", 0.05), // 相关，最相似
                new SearchResult(5L, 5L, "内容E", 0.90)  // 完全不相关，应被过滤
        );

        when(valueOperations.get(anyString())).thenReturn(null); // 缓存未命中
        when(vectorSearchService.search(eq("测试查询"), anyInt())).thenReturn(mockResults);

        List<SearchResult> result = retrievalService.retrieveTopK("测试查询");

        // 验证：只剩 3 条低于阈值 0.35 的（A、C、D），且按 score 升序排列
        assertEquals(3, result.size());
        assertEquals(4L, result.get(0).getChunkId()); // score=0.05，最相关，排第一
        assertEquals(1L, result.get(1).getChunkId()); // score=0.10
        assertEquals(3L, result.get(2).getChunkId()); // score=0.20
    }

    @Test
    void shouldReturnFewerThanTopKWhenNotEnoughRelevantResults() {
        // 只有 1 条低于阈值，验证"宁缺毋滥"逻辑：不会硬凑够 topK=3
        List<SearchResult> mockResults = Arrays.asList(
                new SearchResult(1L, 1L, "内容A", 0.10),
                new SearchResult(2L, 2L, "内容B", 0.80),
                new SearchResult(3L, 3L, "内容C", 0.90)
        );

        when(valueOperations.get(anyString())).thenReturn(null); // 缓存未命中
        when(vectorSearchService.search(eq("怪问题"), anyInt())).thenReturn(mockResults);

        List<SearchResult> result = retrievalService.retrieveTopK("怪问题");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getChunkId());
    }

    @Test
    void shouldFillFileNamesForReferences() {
        // 引用面板展示出处:检索出口统一填充 fileName;同 fileId 只查一次
        List<SearchResult> mockResults = Arrays.asList(
                new SearchResult(1L, 1L, "内容A", 0.10),
                new SearchResult(1L, 2L, "内容A2", 0.20), // 同文件第二个 chunk
                new SearchResult(2L, 3L, "内容B", 0.30)
        );
        FileEntity f1 = new FileEntity();
        f1.setId(1L);
        f1.setFileName("RAG笔记.md");
        FileEntity f2 = new FileEntity();
        f2.setId(2L);
        f2.setFileName("Java基础.pdf");

        when(valueOperations.get(anyString())).thenReturn(null);
        when(vectorSearchService.search(eq("测试"), anyInt())).thenReturn(mockResults);
        when(fileMapper.selectById(1L)).thenReturn(f1);
        when(fileMapper.selectById(2L)).thenReturn(f2);

        List<SearchResult> result = retrievalService.retrieveTopK("测试");

        assertEquals(3, result.size());
        assertEquals("RAG笔记.md", result.get(0).getFileName());
        assertEquals("RAG笔记.md", result.get(1).getFileName()); // 同文件复用一次查询
        assertEquals("Java基础.pdf", result.get(2).getFileName());
        verify(fileMapper, times(1)).selectById(1L);
    }

    @Test
    void shouldHitCacheWithoutCallingVectorSearch() {
        // 缓存命中:直接返回缓存结果,不再走 embedding/检索/重排
        List<SearchResult> cached = List.of(new SearchResult(9L, 9L, "缓存内容", 0.01));
        when(valueOperations.get(anyString())).thenReturn(cached);

        List<SearchResult> result = retrievalService.retrieveTopK("测试查询");

        verify(vectorSearchService, never()).search(anyString(), anyInt());
        assertEquals(1, result.size());
        assertEquals(9L, result.get(0).getChunkId());
    }

    @Test
    void shouldInvalidateUserCache() {
        when(redisTemplate.keys("retrieval:100:*")).thenReturn(Set.of("retrieval:100:abc"));

        retrievalService.invalidate(100L);

        verify(redisTemplate).delete(anyCollection());
    }

    @Test
    void shouldScopeSearchByUserWhenLoggedIn() {
        // 登录用户:检索必须走"按用户过滤"路径(防横向越权)
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("u7");
        UserContext.set(user);
        try {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(vectorSearchService.searchForUser(eq("测试查询"), anyInt(), eq(7L)))
                    .thenReturn(List.of(new SearchResult(1L, 1L, "内容A", 0.1)));

            retrievalService.retrieveTopK("测试查询");

            verify(vectorSearchService).searchForUser(eq("测试查询"), anyInt(), eq(7L));
            verify(vectorSearchService, never()).search(anyString(), anyInt());
        } finally {
            UserContext.remove();
        }
    }

    @Test
    void shouldMergeBm25ResultsWhenHybridEnabled() {
        // 混合检索:登录用户 + 向量有结果 → BM25 并入(精确匹配兜底)
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("u7");
        UserContext.set(user);
        try {
            when(valueOperations.get(anyString())).thenReturn(null);
            when(vectorSearchService.searchForUser(eq("AIFLOWTEST2026"), anyInt(), eq(7L)))
                    .thenReturn(List.of(new SearchResult(1L, 1L, "向量内容", 0.1)));
            // BM25 召回一条向量没召回的(专有名词场景)
            when(chunkMapper.selectByFullText(eq(7L), eq("AIFLOWTEST2026"), anyInt()))
                    .thenReturn(List.of(new SearchResult(2L, 2L, "精确匹配内容", 12.0)));

            List<SearchResult> result = retrievalService.retrieveTopK("AIFLOWTEST2026");

            verify(chunkMapper).selectByFullText(eq(7L), eq("AIFLOWTEST2026"), anyInt());
            // 合并后应同时包含向量路和 BM25 路结果
            assertTrue(result.stream().anyMatch(r -> r.getChunkId() == 1L));
            assertTrue(result.stream().anyMatch(r -> r.getChunkId() == 2L));
        } finally {
            UserContext.remove();
        }
    }
}