package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * C3 回归:限流独立组件——首请求设置过期,超限抛业务异常。
 */
class RateLimitServiceTest {

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations;
    private RateLimitService rateLimitService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void firstRequestShouldSetExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertDoesNotThrow(() -> rateLimitService.check(1L, "chat", 10));

        verify(redisTemplate).expire(anyString(), anyLong(), any());
    }

    @Test
    void withinLimitShouldPass() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        assertDoesNotThrow(() -> rateLimitService.check(1L, "chat", 10));

        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void overLimitShouldThrow() {
        when(valueOperations.increment(anyString())).thenReturn(11L);

        assertThrows(BusinessException.class, () -> rateLimitService.check(1L, "chat", 10));
    }

    @Test
    void failOpenWhenRedisDown() {
        // Redis 故障 → fail-open 放行(fail-open + WARN,PO 2026-09-18 拍板),不阻断主流程
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("simulated down"));

        assertDoesNotThrow(() -> rateLimitService.check("ip:1.2.3.4", "register", 5));
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void failOpenWhenExpireFails() {
        // increment 成功但 expire 失败(半故障态)同样 fail-open,不让异常穿透
        when(valueOperations.increment(anyString())).thenReturn(1L);
        doThrow(new RedisConnectionFailureException("simulated down"))
                .when(redisTemplate).expire(anyString(), anyLong(), any());

        assertDoesNotThrow(() -> rateLimitService.check(1L, "chat", 10));
    }
}
