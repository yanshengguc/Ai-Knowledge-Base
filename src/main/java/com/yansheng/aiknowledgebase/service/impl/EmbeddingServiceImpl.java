package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.service.EmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public EmbeddingServiceImpl(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("待向量化的文本不能为空");
        }
        return embeddingModel.embed(text);
    }
}
