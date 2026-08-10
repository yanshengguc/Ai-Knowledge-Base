package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.common.SearchResult;
import com.yansheng.aiknowledgebase.service.impl.PromptServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptServiceImplTest {

    private final PromptServiceImpl promptService = new PromptServiceImpl();

    @Test
    void shouldBuildPromptWithNumberedContext() {
        List<SearchResult> results = Arrays.asList(
                new SearchResult(1L, 1L, "  Spring Boot是一个快速开发框架  ", 0.10),
                new SearchResult(2L, 2L, "MyBatis是一个持久层框架", 0.20)
        );

        String prompt = promptService.buildPrompt("什么是Spring Boot？", results);

        // 验证编号存在
        assertTrue(prompt.contains("资料1：Spring Boot是一个快速开发框架"));
        assertTrue(prompt.contains("资料2：MyBatis是一个持久层框架"));
        // 验证trim生效，前后空格被去掉
        assertFalse(prompt.contains("  Spring Boot"));
        // 验证问题被拼进去
        assertTrue(prompt.contains("什么是Spring Boot？"));
    }

    @Test
    void shouldHandleEmptyResultsGracefully() {
        String prompt = promptService.buildPrompt("这是一个知识库外的问题", Collections.emptyList());

        assertTrue(prompt.contains("未检索到相关资料"));
        assertTrue(prompt.contains("这是一个知识库外的问题"));
    }

    @Test
    void shouldStopAppendingWhenAccumulatedLengthExceedsLimit() {
        // 模拟真实场景：每条约500字符（贴近实际chunkSize），多条累积后超过MAX_CONTEXT_LENGTH=3000
        String normalChunk = "这是一段模拟真实切片长度的测试文本。".repeat(20); // 约300字符左右，可按实际调整

        List<SearchResult> results = Arrays.asList(
                new SearchResult(1L, 1L, normalChunk, 0.05),
                new SearchResult(2L, 2L, normalChunk, 0.10),
                new SearchResult(3L, 3L, normalChunk, 0.15),
                new SearchResult(4L, 4L, normalChunk, 0.20),
                new SearchResult(5L, 5L, normalChunk, 0.25),
                new SearchResult(6L, 6L, normalChunk, 0.30),
                new SearchResult(7L, 7L, normalChunk, 0.35),
                new SearchResult(8L, 8L, normalChunk, 0.40),
                new SearchResult(9L, 9L, normalChunk, 0.45),
                new SearchResult(10L, 10L, normalChunk, 0.50)
        );

        String prompt = promptService.buildPrompt("测试问题", results);

        // 验证最相关的资料1一定被保留
        assertTrue(prompt.contains("资料1："));
        // 验证没有超限太多（允许最后一条把长度推过一点，但不会离谱地超）
        assertTrue(prompt.length() < 4000);
    }
}
