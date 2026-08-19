package com.yansheng.aiknowledgebase.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 工具集:把知识库能力暴露为标准协议(MCP),
 * 任何 MCP Client(Claude Desktop / Cursor / Cherry Studio 等)都能直接调用。
 *
 * 面试讲法:
 *   1. 我的工具原来只能被 Spring AI 自家调用;接 MCP 后,任何 AI 客户端都能通过
 *      标准协议发现和调用我的知识库 —— 这就是 MCP 的"一次编写,处处可用"
 *   2. 通过 ToolCallbackProvider 注册,Spring AI MCP Server starter 自动暴露
 *   3. Streamable HTTP 传输(2025-03-26 规范,取代旧 SSE),支持多客户端
 */
@Slf4j
@Service
public class KnowledgeMcpTools {

    private static final int TOP_K = 5;
    private static final int SNIPPET_MAX = 200;

    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper;

    public KnowledgeMcpTools(VectorSearchService vectorSearchService, ObjectMapper objectMapper) {
        this.vectorSearchService = vectorSearchService;
        this.objectMapper = objectMapper;
    }

    /**
     * 知识库语义检索(对外 MCP 工具)
     */
    @Tool(description = "在知识库中做语义检索:输入自然语言查询,返回最相关的文件列表(含相关度分数与内容摘要)")
    public String knowledge_search(
            @ToolParam(description = "用户的自然语言查询,原样传入,不要改写") String query) {
        log.info(">>> MCP knowledge_search 被调用, query={}", query);
        if (query == null || query.isBlank()) {
            throw new BusinessException("缺少参数query");
        }

        List<SearchResult> results = vectorSearchService.search(query.trim(), TOP_K);

        // 按 fileId 去重,保留最高分;轻量返回
        Map<Long, SearchResult> bestByFile = new LinkedHashMap<>();
        for (SearchResult r : results) {
            if (r.getFileId() == null) {
                continue;
            }
            SearchResult prev = bestByFile.get(r.getFileId());
            if (prev == null || r.getScore() > prev.getScore()) {
                bestByFile.put(r.getFileId(), r);
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchResult r : bestByFile.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", r.getFileId());
            item.put("score", r.getScore());
            item.put("snippet", truncate(r.getContent(), SNIPPET_MAX));
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", items);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException("结果序列化失败");
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
