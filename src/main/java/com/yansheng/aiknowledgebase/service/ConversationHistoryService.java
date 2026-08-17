package com.yansheng.aiknowledgebase.service;

import java.util.List;
import java.util.Map;

/**
 * 会话记忆服务:基于 Redis 保存多轮对话历史(动态上下文)
 * - 按 userId 一个会话(后续可扩展 sessionId)
 * - 保留最近 MAX_HISTORY 条,超过丢弃最老(窗口截断)
 * - TTL 无操作自动过期
 */
public interface ConversationHistoryService {

    /** 读取最近历史(含当前之前的 N 条),空则返回空列表 */
    List<Map<String, String>> getHistory(Long userId);

    /** 追加一条消息(role: user / assistant) */
    void append(Long userId, String role, String content);

    /** 清空会话历史 */
    void clear(Long userId);
}
