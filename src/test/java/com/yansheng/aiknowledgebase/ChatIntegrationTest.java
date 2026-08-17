package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 多轮对话集成测试:验证「会话记忆 + RAG 问答」流程跑通
 * - 注册/登录拿 token → 两轮 chat → 验证回答非空 + Redis 历史已写入(4 条)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private com.yansheng.aiknowledgebase.utils.JwtUtil jwtUtil;

    private String token;
    private Long userId;

    @BeforeEach
    void setup() throws Exception {
        long ts = System.currentTimeMillis();
        String username = "chat-" + ts;
        String pass = "ChatPass123";

        // 注册 + 登录拿 token
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + pass + "\",\"nickname\":\"chat\"}"))
                .andExpect(status().isOk());
        String loginBody = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(loginBody).get("data").asText();
        assertFalse(token.isBlank(), "登录应返回 token");

        // 从 token 解析真实 userId(会话隔离键)
        userId = jwtUtil.parseToken(token).get("id", Long.class);
        redisTemplate.delete("chat:" + userId);
    }

    @Test
    void chatShouldPersistHistoryAcrossRounds() throws Exception {
        // 第一轮
        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好,请问这个知识库能做什么?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNotEmpty());

        // 第二轮(引用第一轮,验证动态上下文)
        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"那你刚才说能做什么?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNotEmpty());

        // 验证 Redis 中已保存 2 轮 4 条消息(user + assistant ×2)
        Object value = redisTemplate.opsForValue().get("chat:" + userId);
        assertNotNull(value, "Redis 应存在会话历史");
        List<?> history = (List<?>) value;
        assertEquals(4, history.size(), "两轮对话应存 4 条消息");
        assertTrue(((Map<?, ?>) history.get(0)).get("role").equals("user"));
        assertTrue(((Map<?, ?>) history.get(3)).get("role").equals("assistant"));
    }
}
