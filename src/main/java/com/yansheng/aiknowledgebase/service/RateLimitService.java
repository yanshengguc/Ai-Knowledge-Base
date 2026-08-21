package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 独立限流组件(职责归位:Controller 只做参数绑定,限流是横切关注点)。
 * 基于 Redis 计数器:每用户每分钟最多 maxPerMinute 次。
 */
@Service
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 校验某用户某动作的限流,超限抛 {@link BusinessException}。
     *
     * @param userId        用户 id
     * @param action        动作标识(如 "chat"),用于隔离不同接口的计数
     * @param maxPerMinute  每分钟上限
     */
    public void check(Long userId, String action, int maxPerMinute) {
        String rateKey = "rate:" + action + ":" + userId;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(rateKey, 1, TimeUnit.MINUTES);
        }
        if (count != null && count > maxPerMinute) {
            throw new BusinessException("请求太频繁,请稍后再试");
        }
    }
}
