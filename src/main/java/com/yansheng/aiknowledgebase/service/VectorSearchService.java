package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.SearchResult;

import java.util.List;

public interface VectorSearchService {
    List<SearchResult> search(String query, int topK);

    /**
     * 按用户隔离检索:只在该用户拥有的文件范围内召回(多用户数据隔离,供 MCP/工具调用)。
     */
    List<SearchResult> searchForUser(String query, int topK, Long userId);
}
