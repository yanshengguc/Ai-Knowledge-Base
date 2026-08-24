package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.dto.LoginDTO;
import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.UserMapper;
import com.yansheng.aiknowledgebase.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A1 密码安全回归测试:注册必须 BCrypt 哈希入库,登录必须 matches 校验
 * 验证 2026-08-17 密码明文修复真实生效
 */
@SpringBootTest
@ActiveProfiles("local")
class UserSecurityTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;


    private LoginDTO buildLoginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private UserRegisterDTO buildRegisterDTO(String username, String password) {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setNickname("sec-test");
        return dto;
    }

    @Test
    void testRegisterStoresHashedPassword() {
        // 注册后,库里存的必须是 BCrypt 哈希,不能是明文
        String username = "sec-hash-" + System.currentTimeMillis();
        String rawPassword = "raw-password-123";
        userService.register(buildRegisterDTO(username, rawPassword));

        UserEntity saved = userMapper.getUserByName(username);
        assertNotNull(saved, "注册后应能查到用户");
        assertNotEquals(rawPassword, saved.getPassword(), "密码绝不能明文入库");
        assertTrue(saved.getPassword().startsWith("$2"), "密码应为 BCrypt 哈希(前缀 $2)");
    }

    @Test
    void testLoginSuccessWithCorrectPassword() {
        String username = "sec-login-" + System.currentTimeMillis();
        String password = "secret-pass";
        userService.register(buildRegisterDTO(username, password));

        String token = userService.login(buildLoginDTO(username, password));
        assertNotNull(token);
        assertFalse(token.isBlank(), "登录成功应返回非空 token");
    }

    @Test
    void testLoginFailsWithWrongPassword() {
        String username = "sec-wrong-" + System.currentTimeMillis();
        userService.register(buildRegisterDTO(username, "correct-pass"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(buildLoginDTO(username, "wrong-pass")));
        assertEquals("密码错误", ex.getMessage());
    }

    @Test
    void testRegisterDuplicateUsernameRejected() {
        String username = "sec-dup-" + System.currentTimeMillis();
        userService.register(buildRegisterDTO(username, "pass-1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(buildRegisterDTO(username, "pass-2")));
        assertEquals("用户已经存在", ex.getMessage());
    }

    @Test
    void testRegisterEmptyPasswordRejected() {
        // 安全修复回归:空密码注册曾被攻防脚本实测利用(空密码可注册并登录)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(buildRegisterDTO("sec-emptypw-" + System.currentTimeMillis(), "")));
        assertEquals("密码不能为空", ex.getMessage());
    }

    @Test
    void testRegisterBlankUsernameRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(buildRegisterDTO("   ", "pass-1")));
        assertEquals("用户名不能为空", ex.getMessage());
    }
}
