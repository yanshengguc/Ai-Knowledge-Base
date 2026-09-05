package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重排分数下限淘汰:relevance_score 低于 rerank.min-score 的候选不进上下文。
 * 纯单测直测包级方法 reorderByScore(不发起真实 HTTP)。
 */
class RerankScoreFilterTest {

    private RerankServiceImpl rerankService;

    @BeforeEach
    void setUp() {
        rerankService = new RerankServiceImpl(new ObjectMapper());
        ReflectionTestUtils.setField(rerankService, "minScore", 0.3);
    }

    @Test
    void shouldDropCandidatesBelowMinScore() throws Exception {
        // 3 候选:2 条相关(0.9/0.6),1 条噪声(0.05)——噪声必须被淘汰,即使数量没凑满 topN
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "JVM 垃圾回收", 0.5),
                new SearchResult(2L, 2L, "NBA 比赛结果", 0.5),
                new SearchResult(3L, 3L, "Spring Boot 异步上传", 0.5));
        String rawJson = "{\"results\":["
                + "{\"index\":0,\"relevance_score\":0.9},"
                + "{\"index\":2,\"relevance_score\":0.6},"
                + "{\"index\":1,\"relevance_score\":0.05}]}";

        List<SearchResult> result = rerankService.reorderByScore(candidates, rawJson, 3);

        assertEquals(2, result.size(), "低分噪声应被淘汰,不硬凑 topN");
        assertEquals(1L, result.get(0).getChunkId(), "最高分 0.9 排第一");
        assertEquals(3L, result.get(1).getChunkId(), "次高分 0.6 排第二");
    }

    @Test
    void shouldTruncateToTopNAfterFiltering() throws Exception {
        // 淘汰后再截断:4 候选 3 条过线,topN=2 只留前 2
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "A", 0.5),
                new SearchResult(2L, 2L, "B", 0.5),
                new SearchResult(3L, 3L, "C", 0.5),
                new SearchResult(4L, 4L, "D", 0.5));
        String rawJson = "{\"results\":["
                + "{\"index\":0,\"relevance_score\":0.95},"
                + "{\"index\":1,\"relevance_score\":0.80},"
                + "{\"index\":2,\"relevance_score\":0.40},"
                + "{\"index\":3,\"relevance_score\":0.10}]}";

        List<SearchResult> result = rerankService.reorderByScore(candidates, rawJson, 2);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getChunkId());
        assertEquals(2L, result.get(1).getChunkId());
    }

    @Test
    void shouldReturnEmptyWhenAllBelowMinScore() throws Exception {
        // 全部低于阈值 = 知识库无相关资料 → 返回空列表,让上层如实作答,不硬凑
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "A", 0.5),
                new SearchResult(2L, 2L, "B", 0.5));
        String rawJson = "{\"results\":["
                + "{\"index\":0,\"relevance_score\":0.2},"
                + "{\"index\":1,\"relevance_score\":0.1}]}";

        List<SearchResult> result = rerankService.reorderByScore(candidates, rawJson, 3);

        assertTrue(result.isEmpty(), "全部低于下限应返回空,宁缺毋滥");
    }

    @Test
    void shouldDropMissingScoreAsBelowThreshold() throws Exception {
        // 未拿到重排分(响应缺条目)的候选按 -1.0 处理,同样被淘汰
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "A", 0.5),
                new SearchResult(2L, 2L, "B", 0.5));
        String rawJson = "{\"results\":[{\"index\":0,\"relevance_score\":0.88}]}";

        List<SearchResult> result = rerankService.reorderByScore(candidates, rawJson, 3);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getChunkId());
    }

    @Test
    void shouldFallbackToRawOrderWhenNoScoresAtAll() throws Exception {
        // 响应 200 但解析不出任何分数(结构异常/空 results)→ 视为重排失败,
        // 降级返回粗排原顺序,不能当"全部低于阈值"误淘汰成空列表
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "A", 0.5),
                new SearchResult(2L, 2L, "B", 0.5));
        String rawJson = "{\"error\":{\"message\":\"unexpected\"}}";

        List<SearchResult> result = rerankService.reorderByScore(candidates, rawJson, 3);

        assertEquals(2, result.size(), "无分数时应降级保留全部候选");
        assertEquals(1L, result.get(0).getChunkId(), "保持粗排原顺序");
        assertEquals(2L, result.get(1).getChunkId());
    }

    @Test
    void shouldKeepAllWhenMinScoreZero() throws Exception {
        // min-score=0 关闭淘汰:所有拿到分数的候选保留(旧行为回退开关)
        ReflectionTestUtils.setField(rerankService, "minScore", 0.0);
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "A", 0.5),
                new SearchResult(2L, 2L, "B", 0.5));
        String rawJson = "{\"results\":["
                + "{\"index\":0,\"relevance_score\":0.2},"
                + "{\"index\":1,\"relevance_score\":0.1}]}";

        List<SearchResult> result = rerankService.reorderByScore(candidates, rawJson, 3);

        assertEquals(2, result.size(), "min-score=0 应保留全部有分候选");
        assertEquals(1L, result.get(0).getChunkId());
        assertEquals(2L, result.get(1).getChunkId());
    }

    @Test
    void shouldSupportBoChaResponseFormat() throws Exception {
        // 兼容博查格式:results 包在 data 下
        List<SearchResult> candidates = Arrays.asList(
                new SearchResult(1L, 1L, "A", 0.5),
                new SearchResult(2L, 2L, "B", 0.5));
        String rawJson = "{\"data\":{\"results\":["
                + "{\"index\":1,\"relevance_score\":0.85},"
                + "{\"index\":0,\"relevance_score\":0.02}]}}";

        List<SearchResult> result = rerankService.reorderByScore(candidates, rawJson, 3);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getChunkId());
    }
}
