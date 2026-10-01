package com.yansheng.aiknowledgebase.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 管理端全局概览(只读聚合)。
 * 全站口径(不限当前用户),与个人页 /api/token-usage/summary 的用户口径区分开。
 */
@Setter
@Getter
public class AdminOverviewVO {

    private long userCount;

    private long knowledgeCount;

    private long fileCount;

    private long chunkCount;

    /** 文件处理状态分布:SUCCESS/FAILED/PROCESSING -> 数量 */
    private Map<String, Long> fileStatus;

    /** 全站 Token 用量与估算成本(含 chat 与 embedding) */
    private Map<String, Object> tokenUsage;
}