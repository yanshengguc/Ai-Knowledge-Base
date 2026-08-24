package com.yansheng.aiknowledgebase.service;

import java.util.Map;

public interface TokenUsageService {

    /** 记录一次对话用量(归属用户)。失败仅告警,不影响对话主流程 */
    void recordChat(Long userId, long promptTokens, long completionTokens);

    /** 记录一次 Embedding 用量(全局,不归属单用户)。失败仅告警 */
    void recordEmbedding(long promptTokens);

    /** 用量汇总:我的对话(今日/本月/累计) + 全局向量化 + 近 14 天趋势 */
    Map<String, Object> summary(Long userId);
}
