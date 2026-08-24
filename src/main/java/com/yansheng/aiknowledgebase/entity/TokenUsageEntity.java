package com.yansheng.aiknowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
public class TokenUsageEntity {
    private Long id;
    /** 归属用户;NULL 表示全局/共享消耗(如向量化) */
    private Long userId;
    private String model;
    private String type;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal costCny;
    private LocalDateTime createTime;
}
