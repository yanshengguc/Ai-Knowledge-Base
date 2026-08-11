package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class VectorSearchServiceImpl implements VectorSearchService {
private final EmbeddingService embeddingService;
private final VectorStoreService vectorStoreService;

    public VectorSearchServiceImpl(EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public List<SearchResult> search(String query, int topK) {
       float[] queryVector=embeddingService.embed(query);
           return     vectorStoreService.search(queryVector,topK);
    }
}
