package com.yansheng.aiknowledgebase.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final KnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;
    private final ChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    public KnowledgeMcpTools(VectorSearchService vectorSearchService,
                             KnowledgeMapper knowledgeMapper,
                             FileMapper fileMapper,
                             ChunkMapper chunkMapper,
                             ObjectMapper objectMapper) {
        this.vectorSearchService = vectorSearchService;
        this.knowledgeMapper = knowledgeMapper;
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 知识库语义检索(对外 MCP 工具)
     * 安全:匿名 MCP 客户端(MCP 握手不需要鉴权)不允许读取业务数据——
     * 请求需在 Header 携带 Bearer JWT,MCP Client 配置中可声明 headers。
     */
    @Tool(description = "在知识库中做语义检索:输入自然语言查询,返回最相关的文件列表(含相关度分数与内容摘要)")
    public String knowledge_search(
            @ToolParam(description = "用户的自然语言查询,原样传入,不要改写") String query) {
        log.info(">>> MCP knowledge_search 被调用, query={}", query);
        if (query == null || query.isBlank()) {
            throw new BusinessException("缺少参数query");
        }

        Long userId = requireUser();

        // 按用户隔离检索:只在该用户拥有的文件范围内召回
        List<SearchResult> results;
        try {
            results = vectorSearchService.searchForUser(query.trim(), TOP_K, userId);
        } catch (Exception e) {
            // 向量库不可用时优雅返回空结果,不中断 MCP 调用方
            log.error("MCP knowledge_search 向量检索失败,返回空结果: {}", e.getMessage());
            results = List.of();
        }

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

    /**
     * 知识库概况统计(对外 MCP 工具)
     * 安全:同样需要认证,且只统计当前用户自己的数据。
     */
    @Tool(description = "统计知识库概况:知识条目数/文件总数/切片总数/文件处理状态分布")
    public String knowledge_stats() {
        log.info(">>> MCP knowledge_stats 被调用");
        Long userId = requireUser();

        // 一条 SQL 聚合统计,消除 selectAll + N+1 查询
        Map<String, Object> counts = knowledgeMapper.selectStatsByUserId(userId);
        List<Map<String, Object>> statusRows = fileMapper.selectStatusSummaryByUserId(userId);
        Map<String, Integer> statusSummary = new LinkedHashMap<>();
        for (Map<String, Object> row : statusRows) {
            Object status = row.get("status");
            Object count = row.get("count");
            statusSummary.put(String.valueOf(status),
                    count == null ? 0 : ((Number) count).intValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeCount", counts.getOrDefault("knowledgeCount", 0));
        result.put("fileCount", counts.getOrDefault("fileCount", 0));
        result.put("chunkCount", counts.getOrDefault("chunkCount", 0));
        result.put("statusSummary", statusSummary);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException("结果序列化失败");
        }
    }

    /**
     * 当前时间(对外 MCP 工具)
     */
    @Tool(description = "获取当前本地时间(格式 yyyy-MM-dd HH:mm:ss),用于需要实时时间的场景")
    public String time_now() {
        log.info(">>> MCP time_now 被调用");
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 认证门禁:MCP 工具必须携带有效 JWT 才允许访问业务数据 */
    private Long requireUser() {
        Long userId = com.yansheng.aiknowledgebase.utils.UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("MCP 工具需要认证:请在请求头携带 Bearer JWT(Authorization: Bearer <token>)");
        }
        return userId;
    }
}
