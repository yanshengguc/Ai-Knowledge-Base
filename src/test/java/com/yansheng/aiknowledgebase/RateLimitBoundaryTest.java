package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0/P1-资金: Chat 限流边界(10 次/分钟)。
 * 限流是 LLM API 的最后一道挡刷防线:阈值 off-by-one 或计数失效,
 * 一个循环脚本就能一晚烧光 API 余额。
 * 用真实 Redis(独立 userId 隔离,测后清理 key)。
 */
@SpringBootTest
@ActiveProfiles("local")
class RateLimitBoundaryTest {

    private static final int MAX_PER_MINUTE = 10;

    @Autowired
    private RateLimitService rateLimitService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private long uid;

    private long uid() {
        if (uid == 0) {
            uid = System.currentTimeMillis();
        }
        return uid;
    }

    @AfterEach
    void cleanup() {
        if (uid != 0) {
            redisTemplate.delete("rate:chat:" + uid);
        }
    }

    @Test
    void exactlyTenRequestsPassAndEleventhRejected() {
        // 前 10 次:全部放行(含第 10 次,边界含在限内)
        for (int i = 1; i <= MAX_PER_MINUTE; i++) {
            assertDoesNotThrow(() -> rateLimitService.check(uid(), "chat", MAX_PER_MINUTE),
                    "第 " + i + " 次不应被限流");
        }
        // 第 11 次:拒绝,且错误信息可读
        BusinessException e = assertThrows(BusinessException.class,
                () -> rateLimitService.check(uid(), "chat", MAX_PER_MINUTE));
        assertTrue(e.getMessage().contains("频繁"), "限流提示应包含'频繁',实际=" + e.getMessage());
    }

    @Test
    void differentUsersHaveIndependentCounters() {
        long uidB = uid() + 1;
        try {
            for (int i = 0; i < MAX_PER_MINUTE; i++) {
                rateLimitService.check(uidB, "chat", MAX_PER_MINUTE);
            }
            // B 已打满,A 不受影响
            assertDoesNotThrow(() -> rateLimitService.check(uid(), "chat", MAX_PER_MINUTE));
        } finally {
            redisTemplate.delete("rate:chat:" + uidB);
        }
    }
}
