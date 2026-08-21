package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.SearchResult;

import java.util.List;

public interface RetrievalService {
    List<SearchResult> retrieveTopK(String queryText);

    /**
     * 失效某个用户的检索结果缓存。
     * 知识内容变更(如新上传文件)后调用,避免旧缓存污染新检索。
     */
    void invalidate(Long userId);
}
