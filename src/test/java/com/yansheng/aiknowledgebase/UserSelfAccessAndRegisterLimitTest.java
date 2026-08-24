package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-权限/P1-防刷: 用户接口越权与注册限流(HTTP 层)。
 *  - GET /api/user/{id}:仅允许查自己(修复用户名枚举 IDOR)
 *  - POST /api/user/register:同 IP 每分钟 5 次(修复批量注册刷库)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserSelfAccessAndRegisterLimitTest {

    private static final String RATE_KEY = "rate:register:ip:127.0.0.1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long uidA;
    private String tokenA;
    private Long uidB;

    @BeforeEach
    void setUp() throws Exception {
        // 清限流计数,保证本类内用例确定性(与其他测试类同 IP 互不干扰)
        redisTemplate.delete(RATE_KEY);
        long ts = System.currentTimeMillis();
        uidA = registerAndLogin("selfacc-a-" + ts, "Pass-123456");
        tokenA = login("selfacc-a-" + ts, "Pass-123456");
        uidB = registerAndLogin("selfacc-b-" + ts, "Pass-123456");
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RATE_KEY);
    }

    private Long registerAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"nickname\":\"t\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String token = login(username, password);
        return jwtUtil.parseToken(token).get("id", Long.class);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").asText();
    }

    @Test
    void selfProfileReadable() throws Exception {
        mockMvc.perform(get("/api/user/" + uidA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(uidA));
    }

    @Test
    void othersProfileDenied() throws Exception {
        // A 查 B 的资料:必须"权限不足",不能回 username(用户名枚举修复)
        MvcResult result = mockMvc.perform(get("/api/user/" + uidB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        String body = new String(result.getResponse().getContentAsString().getBytes(ISO_8859_1), UTF_8);
        assertTrue(body.contains("权限不足"), "应返回权限不足,实际=" + body);
        assertTrue(!body.contains("selfacc-b-"), "不得泄漏他人用户名");
    }

    @Test
    void registerRateLimitedPerIp() throws Exception {
        // setUp 已注册 2 个(计数 2);再注册 3 个到 5,第 6 次必须被限流
        long ts = System.currentTimeMillis();
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"selfacc-spam-" + ts + "-" + i + "\",\"password\":\"Pass-123456\"}"))
                    .andExpect(jsonPath("$.code").value(200));
        }
        MvcResult blocked = mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"selfacc-spam-" + ts + "-6\",\"password\":\"Pass-123456\"}"))
                .andReturn();
        String body = new String(blocked.getResponse().getContentAsString().getBytes(ISO_8859_1), UTF_8);
        assertTrue(body.contains("请求太频繁"), "第 6 次注册应被限流,实际=" + body);
    }
}
