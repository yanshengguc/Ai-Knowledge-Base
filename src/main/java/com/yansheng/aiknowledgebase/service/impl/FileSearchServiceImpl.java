package com.yansheng.aiknowledgebase.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.FileSearchService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * file_search 工具:根据自然语言查询在知识库中做语义检索,返回相关文件的 fileId 列表。
 *
 * 与 file_trace 构成"先搜后查"序列依赖链:
 *   file_search(query) → 拿到 fileId → file_trace(fileId) → 文件详情/状态
 * 设计要点(面试可讲):
 *   1. query 原样直传,不做关键词提炼(向量检索不需要;提炼丢语义)
 *   2. 返回轻量(fileId + score + snippet),整段 content 不进上下文,避免稀释模型注意力
 *   3. 描述里写清"先/后"关系与反例,引导模型序列调用而非乱调
 */
@Slf4j
@Service
public class FileSearchServiceImpl implements FileSearchService {

    private static final int TOP_K = 5;
    private static final int SNIPPET_MAX = 200;

    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper;

    public FileSearchServiceImpl(VectorSearchService vectorSearchService, ObjectMapper objectMapper) {
        this.vectorSearchService = vectorSearchService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "file_search";
    }

    @Override
    public String getToolDescription() {
        return "当用户想检索/查询知识库中的文档内容时使用。例如:『有没有讲JVM调优的资料?』『和Redis缓存有关的文档有哪些?』。"
                + "输入:用户的自然语言查询(query,原样传入,不要改写)。"
                + "输出:相关文件的fileId列表(含相关度分数与内容摘要)。"
                + "注意:① 若需要查看某个文件的具体处理状态、大小或下载链接,"
                + "请基于返回的fileId再调用file_trace;"
                + "② 若问题不需要查知识库(寒暄、通用知识等),直接回答,不要调用本工具。";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "用户的自然语言查询,原样传入,不要改写"
                    }
                  },
                  "required": ["query"]
                }
                """;
    }

    @Override
    public String execute(Map<String, Object> params) {
        log.info(">>> file_search被调用,参数: {}", params);

        Object rawQuery = params.get("query");
        if (rawQuery == null || rawQuery.toString().isBlank()) {
            throw new BusinessException("缺少参数query");
        }
        String query = rawQuery.toString().trim();

        List<SearchResult> results;
        try {
            // 用户隔离(防横向越权):登录请求只在本人文件范围内召回,与 RetrievalServiceImpl 同口径;
            // 未登录(评测/内部分析场景)才走全局检索
            Long userId = com.yansheng.aiknowledgebase.utils.UserContext.getUserId();
            results = (userId != null)
                    ? vectorSearchService.searchForUser(query, TOP_K, userId)
                    : vectorSearchService.search(query, TOP_K);
        } catch (Exception e) {
            // 向量库不可用时优雅返回空结果:ReAct 循环据实回答"未检索到",不抛错中断
            log.error("file_search 向量检索失败,返回空结果: {}", e.getMessage());
            results = List.of();
        }

        // 按 fileId 去重(一个文件的多个chunk可能都命中),保留最高分
        Map<Long, SearchResult> bestByFile = new LinkedHashMap<>();
        for (SearchResult r : results) {
            Long fileId = r.getFileId();
            if (fileId == null) {
                continue;
            }
            SearchResult prev = bestByFile.get(fileId);
            if (prev == null || r.getScore() > prev.getScore()) {
                bestByFile.put(fileId, r);
            }
        }

        // 轻量返回:fileId + score + snippet,不返回整段content
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
