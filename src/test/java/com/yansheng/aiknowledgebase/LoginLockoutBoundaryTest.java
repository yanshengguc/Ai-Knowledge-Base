package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.dto.LoginDTO;
import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-权限: 登录防爆破边界(5 次失败锁 10 分钟)。
 * 核心业务断言:锁定后"正确密码"也必须被拒(锁检查在密码校验之前),
 * 否则攻击者可持续高速撞库,锁定形同虚设。
 */
@SpringBootTest
@ActiveProfiles("local")
class LoginLockoutBoundaryTest {

    private static final String PASSWORD = "correct-pass-123";

    @Autowired
    private UserService userService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String username;

    private String username() {
        if (username == null) {
            username = "lockout-" + System.currentTimeMillis();
            UserRegisterDTO dto = new UserRegisterDTO();
            dto.setUsername(username);
            dto.setPassword(PASSWORD);
            dto.setNickname("lockout-test");
            userService.register(dto);
        }
        return username;
    }

    private String failKey() {
        return "login:fail:" + username();
    }

    @AfterEach
    void cleanup() {
        if (username != null) {
            redisTemplate.delete(failKey());
        }
    }

    private void wrongPassword() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username());
        dto.setPassword("wrong-" + System.nanoTime());
        userService.login(dto);
    }

    @Test
    void fifthFailureLocksAndCorrectPasswordAlsoRejected() {
        // 第 1~5 次错误密码:门内检查通过(计数 4<5),报"用户名或密码错误"(统一文案防枚举)
        for (int i = 1; i <= 5; i++) {
            BusinessException e = assertThrows(BusinessException.class, this::wrongPassword);
            assertEquals("用户名或密码错误", e.getMessage(), "第 " + i + " 次应报用户名或密码错误");
        }

        // 第 6 次:即使密码正确,也必须先撞上锁定(锁检查先于密码校验)
        LoginDTO correct = new LoginDTO();
        correct.setUsername(username());
        correct.setPassword(PASSWORD);
        BusinessException locked = assertThrows(BusinessException.class,
                () -> userService.login(correct));
        assertTrue(locked.getMessage().contains("尝试次数过多"), "应报锁定而非放行,实际=" + locked.getMessage());
    }

    @Test
    void successLoginClearsFailureCounter() {
        assertThrows(BusinessException.class, this::wrongPassword);
        assertThrows(BusinessException.class, this::wrongPassword);

        LoginDTO correct = new LoginDTO();
        correct.setUsername(username());
        correct.setPassword(PASSWORD);
        String token = userService.login(correct);
        assertFalse(token.isBlank(), "正确密码应登录成功");

        // 登录成功必须清计数:否则下次输错一次就提前触发旧账累计
        Object count = redisTemplate.opsForValue().get(failKey());
        assertTrue(count == null || ((Number) count).intValue() == 0,
                "登录成功后失败计数应清除,实际=" + count);
    }
}
