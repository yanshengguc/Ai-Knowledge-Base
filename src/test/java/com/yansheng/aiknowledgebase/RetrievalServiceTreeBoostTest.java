package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.OutlineMapper;
import com.yansheng.aiknowledgebase.service.RerankService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.service.impl.RetrievalServiceImpl;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-114 Phase2 树路由加权:开关零行为 / 命中前置 / 无命中降级 / 异常降级。
 *
 * 设计要点(与实现同构):
 *  - 锚定 chunk 若不在候选池,须在 rerank 截断前补入(点查),再于最终结果中稳定前置——
 *    即便被 rerank 低分截断,也能从补召回池找回并进入 topK(「预留槽位」)。
 *  - 任一环节失败只 log.warn 降级为纯 RAG,不向调用方抛异常、问答不中断。
 */
class RetrievalServiceTreeBoostTest {

    private VectorSearchService vectorSearchService;
    private RerankService rerankService;
    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations;
    private ChunkMapper chunkMapper;
    private FileMapper fileMapper;
    private OutlineMapper outlineMapper;
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
        outlineMapper = mock(OutlineMapper.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // 缓存一律未命中,走真实检索链路
        // BM25 路本组测试不关心,统一置空避免噪声
        when(chunkMapper.selectByFullText(anyLong(), anyString(), anyInt())).thenReturn(List.of());

        retrievalService = new RetrievalServiceImpl(
                vectorSearchService, rerankService, redisTemplate, chunkMapper, fileMapper, outlineMapper);
        ReflectionTestUtils.setField(retrievalService, "topK", 3);
        ReflectionTestUtils.setField(retrievalService, "similarityThreshold", 0.35);
        ReflectionTestUtils.setField(retrievalService, "rerankEnabled", false);
        ReflectionTestUtils.setField(retrievalService, "hybridEnabled", true);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private void login() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("tree_u7");
        UserContext.set(user);
    }

    private void setBoost(boolean enabled) {
        ReflectionTestUtils.setField(retrievalService, "treeBoostEnabled", enabled);
    }

    /** 向量召回 3 条均在阈值内,顺序依赖上游升序(score 越小越相关) */
    private void stubVector() {
        when(vectorSearchService.searchForUser(eq("树查询"), anyInt(), eq(7L)))
                .thenReturn(Arrays.asList(
                        new SearchResult(1L, 1L, "内容A", 0.05),
                        new SearchResult(2L, 2L, "内容B", 0.10),
                        new SearchResult(3L, 3L, "内容C", 0.20)));
    }

    @Test
    void boostDisabledShouldNotQueryOutlineAndKeepBehavior() {
        // 开关关闭 → OutlineMapper 零调用,输出与纯 RAG 完全一致
        setBoost(false);
        login();
        stubVector();

        List<SearchResult> result = retrievalService.retrieveTopK("树查询");

        verify(outlineMapper, never()).selectNodeIdsByUserKeywords(anyLong(), anyList(), anyInt());
        verify(outlineMapper, never()).selectChunkIdsByNodeIds(anyList());
        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).getChunkId());
        assertEquals(2L, result.get(1).getChunkId());
        assertEquals(3L, result.get(2).getChunkId());
    }

    @Test
    void boostHitShouldFrontPlaceAnchorChunkWithinTopK() {
        // 开关开 + 节点命中:锚定 chunk(不在候选池,点查补入)前置,且进入最终 topK
        setBoost(true);
        login();
        stubVector();
        when(outlineMapper.selectNodeIdsByUserKeywords(eq(7L), anyList(), anyInt()))
                .thenReturn(List.of(100L));
        when(outlineMapper.selectChunkIdsByNodeIds(List.of(100L))).thenReturn(List.of(999L));
        when(chunkMapper.selectSearchResultsByIds(eq(7L), eq(List.of(999L))))
                .thenReturn(List.of(new SearchResult(9L, 999L, "锚定章节内容", null)));

        List<SearchResult> result = retrievalService.retrieveTopK("树查询");

        verify(chunkMapper).selectSearchResultsByIds(eq(7L), eq(List.of(999L)));
        assertEquals(3, result.size());
        assertEquals(999L, result.get(0).getChunkId(), "锚定 chunk 必须前置到首位");
        // 其余保持原相对顺序
        assertEquals(1L, result.get(1).getChunkId());
        assertEquals(2L, result.get(2).getChunkId());
    }

    @Test
    void boostHitWithAnchorAlreadyInPoolShouldFrontPlaceWithoutPointQuery() {
        // 锚定 chunk 已在候选池(命中池内 chunk 2)→ 不点查,仅前置
        setBoost(true);
        login();
        stubVector();
        when(outlineMapper.selectNodeIdsByUserKeywords(eq(7L), anyList(), anyInt()))
                .thenReturn(List.of(100L));
        when(outlineMapper.selectChunkIdsByNodeIds(List.of(100L))).thenReturn(List.of(2L));

        List<SearchResult> result = retrievalService.retrieveTopK("树查询");

        verify(chunkMapper, never()).selectSearchResultsByIds(anyLong(), anyList());
        assertEquals(3, result.size());
        assertEquals(2L, result.get(0).getChunkId());
        assertEquals(1L, result.get(1).getChunkId());
        assertEquals(3L, result.get(2).getChunkId());
    }

    @Test
    void boostEnabledButNoNodeHitShouldKeepOriginalOrder() {
        // 开关开 + 无命中(节点空)→ 顺序与纯 RAG 一致,且不点查 chunk
        setBoost(true);
        login();
        stubVector();
        when(outlineMapper.selectNodeIdsByUserKeywords(eq(7L), anyList(), anyInt()))
                .thenReturn(List.of());

        List<SearchResult> result = retrievalService.retrieveTopK("树查询");

        verify(chunkMapper, never()).selectSearchResultsByIds(anyLong(), anyList());
        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).getChunkId());
        assertEquals(2L, result.get(1).getChunkId());
        assertEquals(3L, result.get(2).getChunkId());
    }

    @Test
    void boostExceptionShouldDegradeToPureRagWithoutThrowing() {
        // 开关开 + OutlineMapper 抛异常 → 不外抛,返回纯 RAG 原顺序
        setBoost(true);
        login();
        stubVector();
        when(outlineMapper.selectNodeIdsByUserKeywords(anyLong(), anyList(), anyInt()))
                .thenThrow(new RuntimeException("outline 表查询失败(模拟)"));

        List<SearchResult> result = assertDoesNotThrow(() -> retrievalService.retrieveTopK("树查询"));

        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).getChunkId());
        assertEquals(2L, result.get(1).getChunkId());
        assertEquals(3L, result.get(2).getChunkId());
    }

    @Test
    void boostEnabledButNoUserContextShouldNotQueryOutlineAndKeepBehavior() {
        // 边界:开关开但无用户上下文(userId == null)→ 不做树查询/boost,行为同关闭(与 Sprint 8 基线一致)
        setBoost(true);
        // 不设置 UserContext → userId 为 null,走 search(query, ...) 非用户隔离路径
        when(vectorSearchService.search(eq("树查询"), anyInt()))
                .thenReturn(Arrays.asList(
                        new SearchResult(1L, 1L, "内容A", 0.05),
                        new SearchResult(2L, 2L, "内容B", 0.10),
                        new SearchResult(3L, 3L, "内容C", 0.20)));

        List<SearchResult> result = retrievalService.retrieveTopK("树查询");

        verify(outlineMapper, never()).selectNodeIdsByUserKeywords(anyLong(), anyList(), anyInt());
        verify(outlineMapper, never()).selectChunkIdsByNodeIds(anyList());
        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).getChunkId());
        assertEquals(2L, result.get(1).getChunkId());
        assertEquals(3L, result.get(2).getChunkId());
    }

    @Test
    void boostFallbackAnchorsShouldBeCappedAtOneWhenRerankEnabled() {
        // 边界:rerank 开启,锚定项(2 个)均未被 rerank 选中(仅路由召回补入)→ 前置限流为 1,其余顺序不变
        setBoost(true);
        login();
        stubVector();
        ReflectionTestUtils.setField(retrievalService, "rerankEnabled", true);
        // rerank 只返回向量池里的 1/2/3,两个锚定项(999/998)落选
        when(rerankService.rerank(eq("树查询"), anyList(), eq(3)))
                .thenReturn(Arrays.asList(
                        new SearchResult(1L, 1L, "内容A", 0.05),
                        new SearchResult(2L, 2L, "内容B", 0.10),
                        new SearchResult(3L, 3L, "内容C", 0.20)));
        when(outlineMapper.selectNodeIdsByUserKeywords(eq(7L), anyList(), anyInt()))
                .thenReturn(List.of(100L));
        when(outlineMapper.selectChunkIdsByNodeIds(List.of(100L))).thenReturn(List.of(999L, 998L));
        when(chunkMapper.selectSearchResultsByIds(eq(7L), eq(List.of(999L, 998L))))
                .thenReturn(List.of(
                        new SearchResult(9L, 999L, "锚定1", null),
                        new SearchResult(9L, 998L, "锚定2", null)));

        List<SearchResult> result = retrievalService.retrieveTopK("树查询");

        // 仅 1 个落选锚定项被前置;第 2 个不进入结果(防 LIKE 泛词灌满 topK)
        assertEquals(3, result.size());
        assertEquals(999L, result.get(0).getChunkId());
        assertEquals(1L, result.get(1).getChunkId());
        assertEquals(2L, result.get(2).getChunkId());
        assertFalse(result.stream().anyMatch(r -> r.getChunkId() == 998L));
    }
}
