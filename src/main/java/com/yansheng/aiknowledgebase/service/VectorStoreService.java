package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.common.SearchResult;

import java.util.List;

public interface VectorStoreService {
    void insert(Long chunkId,Long documentId,String content ,float[] vector);
    List<SearchResult> search(float[] vector,int topK);
}
