package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.service.impl.FileSearchServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * file_search 用户隔离(防横向越权):登录请求只在该用户文件范围内召回。
 * 与 RetrievalServiceImpl 的 searchForUser 同口径。
 */
class FileSearchServiceIsolationTest {

    private VectorSearchService vectorSearchService;
    private FileSearchServiceImpl fileSearchService;

    @BeforeEach
    void setUp() {
        vectorSearchService = mock(VectorSearchService.class);
        fileSearchService = new FileSearchServiceImpl(vectorSearchService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private Map<String, Object> params(String query) {
        Map<String, Object> p = new HashMap<>();
        p.put("query", query);
        return p;
    }

    @Test
    void shouldSearchWithinUserScopeWhenLoggedIn() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("u7");
        UserContext.set(user);
        when(vectorSearchService.searchForUser(eq("JVM 调优"), anyInt(), eq(7L)))
                .thenReturn(List.of(new SearchResult(11L, 1L, "用户自己的片段", 0.2)));

        String result = fileSearchService.execute(params("JVM 调优"));

        verify(vectorSearchService).searchForUser(eq("JVM 调优"), anyInt(), eq(7L));
        verify(vectorSearchService, never()).search(anyString(), anyInt());
        assertTrue(result.contains("\"fileId\":11"), "结果应来自用户隔离检索");
    }

    @Test
    void shouldReturnEmptyForNewUserWithNoFiles() {
        // 新用户无文件:searchForUser 直接返回空 → 空结果 JSON,不跨用户召回
        UserEntity user = new UserEntity();
        user.setId(569L);
        user.setUsername("newbie");
        UserContext.set(user);
        when(vectorSearchService.searchForUser(anyString(), anyInt(), eq(569L)))
                .thenReturn(List.of());

        String result = fileSearchService.execute(params("JVM 调优"));

        verify(vectorSearchService, never()).search(anyString(), anyInt());
        assertEquals("{\"results\":[]}", result);
    }

    @Test
    void shouldFallbackToEmptyOnVectorFailure() {
        // 向量库故障:优雅返回空结果 JSON,不抛错中断 ReAct 循环
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("u7");
        UserContext.set(user);
        when(vectorSearchService.searchForUser(anyString(), anyInt(), eq(7L)))
                .thenThrow(new RuntimeException("DashVector 不可用"));

        String result = fileSearchService.execute(params("任意"));

        assertEquals("{\"results\":[]}", result);
    }

    @Test
    void shouldUseGlobalSearchWhenNoUserContext() {
        // 未登录(评测/内部分析)场景:保持原全局检索路径
        when(vectorSearchService.search(eq("全局查询"), anyInt()))
                .thenReturn(List.of(new SearchResult(1L, 1L, "片段", 0.2)));

        String result = fileSearchService.execute(params("全局查询"));

        verify(vectorSearchService).search(eq("全局查询"), anyInt());
        verify(vectorSearchService, never()).searchForUser(anyString(), anyInt(), any());
        assertTrue(result.contains("\"fileId\":1"));
    }
}
