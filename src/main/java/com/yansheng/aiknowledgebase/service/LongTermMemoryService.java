package com.yansheng.aiknowledgebase.service;

import java.util.List;

/**
 * 长期记忆(分层记忆的"长期层"):
 *  - 短期记忆:Redis 会话(chat:{userId},List+窗口截断+TTL)
 *  - 长期记忆:向量库跨会话持久化(用户历史问题/结论,按 user_id 隔离,语义召回)
 *
 * 面试可讲:记忆分三层——工作记忆(内存)/短期(Redis)/长期(向量库);
 * 长期记忆让用户换会话后 AI 仍记得之前的偏好与结论。
 */
public interface LongTermMemoryService {

    /** 写入一条长期记忆(embedding 后存向量库,user_id 隔离) */
    void remember(Long userId, String content);

    /** 按语义召回该用户的长期记忆 TopK(用于注入 Prompt) */
    List<String> recall(Long userId, String query, int topK);
}
