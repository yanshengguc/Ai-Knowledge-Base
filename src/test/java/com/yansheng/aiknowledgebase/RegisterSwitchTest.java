package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.dto.LoginDTO;
import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * register.enabled 开关行为(HTTP 层):
 *  - 关闭时 POST /api/user/register → "注册已关闭"
 *  - 关闭不影响既有账号登录(个人知识库场景:生产关注册,老用户照常用)
 *  - 默认 true 分支由 UserSecurityTest 等既有注册用例隐式覆盖
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "register.enabled=false")
class RegisterSwitchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Test
    void testRegisterRejectedWhenDisabled() throws Exception {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("switch-off-" + System.currentTimeMillis());
        dto.setPassword("some-pass-123");
        dto.setNickname("switch-test");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("注册已关闭"));
    }

    @Test
    void testLoginStillWorksWhenRegisterDisabled() throws Exception {
        // 开关只挡注册,不挡登录:先注册(走 Service 绕过 Controller 开关),再走 HTTP 登录
        String username = "switch-login-" + System.currentTimeMillis();
        userService.register(buildRegister(username, "pass-123"));

        LoginDTO login = new LoginDTO();
        login.setUsername(username);
        login.setPassword("pass-123");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    private UserRegisterDTO buildRegister(String username, String password) {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setNickname("switch-test");
        return dto;
    }
}
