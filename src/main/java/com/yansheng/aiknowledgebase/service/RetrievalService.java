package com.yansheng.aiknowledgebase.service;

import com.aliyun.dashvector.models.Doc;
import com.yansheng.aiknowledgebase.common.SearchResult;

import java.util.List;

public interface RetrievalService {
    List<SearchResult> retrieveTopK(String queryVector);
}
