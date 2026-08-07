package com.yansheng.aiknowledgebase.service;

public interface VectorStoreService {
    void insert(Long chunkId,Long documentId,String content ,float[] vector);
}
