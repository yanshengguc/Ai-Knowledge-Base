package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.TokenUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final TokenUsageService tokenUsageService;

    public EmbeddingServiceImpl(EmbeddingModel embeddingModel, TokenUsageService tokenUsageService) {
        this.embeddingModel = embeddingModel;
        this.tokenUsageService = tokenUsageService;
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("待向量化的文本不能为空");
        }
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        recordUsage(response);
        return response.getResults().get(0).getOutput();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("待向量化的文本列表不能为空");
        }
        // 批量接口:一次请求多文本(底层按 API 限制自动分批),相比逐条循环减少大量网络往返
        EmbeddingResponse response = embeddingModel.embedForResponse(texts);
        recordUsage(response);
        return response.getResults().stream()
                .map(embedding -> embedding.getOutput())
                .toList();
    }

    /** 从响应元数据提取 token 用量(无 usage 字段时静默跳过) */
    private void recordUsage(EmbeddingResponse response) {
        try {
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                Integer prompt = usage.getPromptTokens();
                if (prompt != null && prompt > 0) {
                    tokenUsageService.recordEmbedding(prompt);
                }
            }
        } catch (Exception e) {
            log.warn("提取 embedding 用量失败: {}", e.getMessage());
        }
    }
}
