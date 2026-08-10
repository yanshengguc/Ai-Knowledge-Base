package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.common.SearchResult;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.service.impl.RetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetrievalServiceImplTest {

    private VectorSearchService vectorSearchService;
    private RetrievalServiceImpl retrievalService;

    @BeforeEach
    void setUp() {
        vectorSearchService = mock(VectorSearchService.class);
        retrievalService = new RetrievalServiceImpl(vectorSearchService);
        // 因为 @Value 注入的字段在纯 new 出来的对象里不会自动赋值，手动塞进去
        ReflectionTestUtils.setField(retrievalService, "topK", 3);
        ReflectionTestUtils.setField(retrievalService, "similarityThreshold", 0.35);
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

        when(vectorSearchService.search(eq("怪问题"), anyInt())).thenReturn(mockResults);

        List<SearchResult> result = retrievalService.retrieveTopK("怪问题");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getChunkId());
    }
}