package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.service.ConversationHistoryService;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 会话记忆:Redis List 原子追加 + 窗口截断
 * - 用 RIGHT_PUSH + LTRIM 原子操作,避免"读-改-写"竞态丢消息
 * - 保留最近 MAX_HISTORY 条,TTL 无操作过期
 */
@Service
public class ConversationHistoryServiceImpl implements ConversationHistoryService {

    /** 保留最近 10 条(5 轮对话) */
    private static final int MAX_HISTORY = 10;
    /** 无操作 30 分钟后过期 */
    private static final long TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    public ConversationHistoryServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String key(Long userId) {
        return "chat:" + userId;
    }

    @Override
    public List<Map<String, String>> getHistory(Long userId) {
        try {
            ListOperations<String, Object> ops = redisTemplate.opsForList();
            List<Object> raw = ops.range(key(userId), -MAX_HISTORY, -1);
            if (raw == null || raw.isEmpty()) {
                return new ArrayList<>();
            }
            List<Map<String, String>> history = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof Map) {
                    Map<String, String> msg = new LinkedHashMap<>();
                    for (Map.Entry<Object, Object> e : ((Map<Object, Object>) item).entrySet()) {
                        msg.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                    history.add(msg);
                }
            }
            return history;
        } catch (Exception ignored) {
            // Redis 异常降级:当作无历史,不影响问答主流程
            return new ArrayList<>();
        }
    }

    @Override
    public void append(Long userId, String role, String content) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        try {
            ListOperations<String, Object> ops = redisTemplate.opsForList();
            // 原子追加 + 只保留最近 MAX_HISTORY 条 + 刷新 TTL
            ops.rightPush(key(userId), msg);
            ops.trim(key(userId), -MAX_HISTORY, -1);
            redisTemplate.expire(key(userId), TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // Redis 异常不影响问答
        }
    }

    @Override
    public void clear(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (Exception ignored) {
        }
    }
}
