package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 独立限流组件(职责归位:Controller 只做参数绑定,限流是横切关注点)。
 * 基于 Redis 计数器:每用户每分钟最多 maxPerMinute 次。
 *
 * <p>Redis 故障降级策略:<b>fail-open + WARN 留痕</b>(PO 2026-09-18 拍板)——
 * 限流是可用性防线而非数据防线,Redis 挂掉时放行并记 WARN(教训:降级必须可观测,
 * 否则就是下一次"静默 16 天");成本侧仍有 DB 口径的每日配额(ChatQuotaService)兜底。
 * 已接受的权衡:Redis 故障窗口内注册防刷敞开。
 */
@Slf4j
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
        check(String.valueOf(userId), action, maxPerMinute);
    }

    /**
     * 通用限流:identity 可以是 userId,也可以是 "ip:x.x.x.x"(注册等未登录场景)。
     * key 结构 rate:{action}:{identity},与既有 chat 限流兼容。
     * Redis 不可用时 fail-open 放行(WARN 留痕),不阻断主流程。
     */
    public void check(String identity, String action, int maxPerMinute) {
        String rateKey = "rate:" + action + ":" + identity;
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(rateKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(rateKey, 1, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("Redis 不可用,限流降级为放行(故障开窗): action={} identity={}", action, identity, e);
            return;
        }
        if (count != null && count > maxPerMinute) {
            throw new BusinessException("请求太频繁,请稍后再试");
        }
    }
}
