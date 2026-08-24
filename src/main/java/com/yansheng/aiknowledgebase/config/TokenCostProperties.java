package com.yansheng.aiknowledgebase.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Token 计价配置(元/百万 tokens),用于成本"估算"而非账单。
 * 默认值按 DeepSeek V4-Flash 空闲档/缓存未命中口径,高峰档约 2 倍,请按官网校准。
 */
@Component
@ConfigurationProperties(prefix = "token-cost")
@Getter
@Setter
public class TokenCostProperties {

    private Map<String, ModelPrice> models = new HashMap<>();

    @Getter
    @Setter
    public static class ModelPrice {
        private double inputPerM;
        private double outputPerM;
    }

    public BigDecimal estimate(String model, long promptTokens, long completionTokens) {
        ModelPrice price = models.get(model);
        if (price == null) {
            return BigDecimal.ZERO;
        }
        double cost = promptTokens / 1_000_000.0 * price.getInputPerM()
                + completionTokens / 1_000_000.0 * price.getOutputPerM();
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }
}
