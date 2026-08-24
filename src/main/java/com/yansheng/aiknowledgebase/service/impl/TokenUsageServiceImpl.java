package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.config.TokenCostProperties;
import com.yansheng.aiknowledgebase.entity.TokenUsageEntity;
import com.yansheng.aiknowledgebase.mapper.TokenUsageMapper;
import com.yansheng.aiknowledgebase.service.TokenUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TokenUsageServiceImpl implements TokenUsageService {

    private static final int TREND_DAYS = 14;

    private final TokenUsageMapper tokenUsageMapper;
    private final TokenCostProperties costProperties;
    private final String chatModel;
    private final String embeddingModel;

    public TokenUsageServiceImpl(TokenUsageMapper tokenUsageMapper,
                                 TokenCostProperties costProperties,
                                 @Value("${spring.ai.openai.chat.options.model:unknown}") String chatModel,
                                 @Value("${spring.ai.dashscope.embedding.options.model:unknown}") String embeddingModel) {
        this.tokenUsageMapper = tokenUsageMapper;
        this.costProperties = costProperties;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void recordChat(Long userId, long promptTokens, long completionTokens) {
        // 统计类旁路逻辑:任何异常只告警,绝不打断对话
        try {
            insert(userId, chatModel, "chat", promptTokens, completionTokens);
        } catch (Exception e) {
            log.warn("记录对话 token 用量失败: {}", e.getMessage());
        }
    }

    @Override
    public void recordEmbedding(long promptTokens) {
        try {
            insert(null, embeddingModel, "embedding", promptTokens, 0);
        } catch (Exception e) {
            log.warn("记录 embedding token 用量失败: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> summary(Long userId) {
        Map<String, Object> chat = orEmpty(tokenUsageMapper.selectChatSummaryByUserId(userId));
        Map<String, Object> embedding = orEmpty(tokenUsageMapper.selectEmbeddingSummary());
        List<Map<String, Object>> trend = tokenUsageMapper.selectDailyTrend(userId, TREND_DAYS);

        Map<String, Object> result = new HashMap<>();
        result.put("chat", chat);
        result.put("embedding", embedding);
        result.put("trend", trend);
        return result;
    }

    private void insert(Long userId, String model, String type, long promptTokens, long completionTokens) {
        if (promptTokens <= 0 && completionTokens <= 0) {
            return;
        }
        TokenUsageEntity entity = new TokenUsageEntity();
        entity.setUserId(userId);
        entity.setModel(model);
        entity.setType(type);
        entity.setPromptTokens((int) promptTokens);
        entity.setCompletionTokens((int) completionTokens);
        entity.setTotalTokens((int) (promptTokens + completionTokens));
        entity.setCostCny(costProperties.estimate(model, promptTokens, completionTokens));
        tokenUsageMapper.insert(entity);
    }

    private Map<String, Object> orEmpty(Map<String, Object> map) {
        return map != null ? map : new HashMap<>();
    }
}
