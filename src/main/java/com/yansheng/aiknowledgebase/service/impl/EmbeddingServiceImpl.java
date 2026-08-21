package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.service.EmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("待向量化的文本列表不能为空");
        }
        // 批量接口:一次请求多文本(底层按 API 限制自动分批),相比逐条循环减少大量网络往返
        return embeddingModel.embedForResponse(texts).getResults().stream()
                .map(embedding -> embedding.getOutput())
                .toList();
    }
}
