package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.common.SearchResult;

import java.util.List;

public interface VectorSearchService {
    List<SearchResult> search(String query, int topK);
}
