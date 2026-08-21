package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;

import java.util.List;

public interface VectorStoreService {
    void insert(Long chunkId, Long fileId, String content, float[] vector);

    /**
     * 批量插入:一次请求写入多条向量,与 {@link #insert} 语义一致。
     * chunks 与 vectors 按下标一一对应。
     */
    void insertBatch(Long fileId, List<ChunkEntity> chunks, List<float[]> vectors);

    List<SearchResult> search(float[] vector,int topK);
}
