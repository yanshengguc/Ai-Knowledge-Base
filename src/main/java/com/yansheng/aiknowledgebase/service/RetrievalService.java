package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.SearchResult;

import java.util.List;

public interface RetrievalService {
    List<SearchResult> retrieveTopK(String queryText);
}
