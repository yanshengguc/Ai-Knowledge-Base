package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;

import java.util.List;

public interface IndexingService {
    void indexChunks(Long documentId, List<ChunkEntity> chunkList);
}