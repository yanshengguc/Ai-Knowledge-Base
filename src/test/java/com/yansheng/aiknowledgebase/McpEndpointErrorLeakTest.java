package com.yansheng.aiknowledgebase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-119 安全回归(HTTP 级):/api/mcp-endpoint 的异常出口(无 token / 无效 session /
 * 畸形 session)响应体不得泄露 Java 堆栈(类名/文件名/行号);正常 initialize 行为零变化;
 * 同时守住硬约束——普通 @RestController 的畸形 JSON 行为不变(200 + 系统异常)。
 *
 * 背景:该端点由 Spring AI 注册为函数式端点(RouterFunction),不经过 @RestControllerAdvice。
 * 传输层用 ServerResponse.body(new McpError(...)) 正常返回错误对象,而 McpError 继承
 * RuntimeException,被 Jackson 默认序列化时带出 stackTrace 数组 -> 内部结构泄露。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class McpEndpointErrorLeakTest {

    private static final String ENDPOINT = "/api/mcp-endpoint";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> post(String sessionId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 传输层要求 Accept 同时含 text/event-stream 与 application/json,否则提前 400
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        if (sessionId != null) {
            headers.add("mcp-session-id", sessionId);
        }
        return rest.exchange(url(ENDPOINT), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    /** 断言响应体不含任何 Java 堆栈痕迹(类名/文件名/行号/栈帧) */
    private void assertNoInternalLeak(String tag, ResponseEntity<String> resp) {
        String body = resp.getBody();
        System.out.println("[B-119][" + tag + "] status=" + resp.getStatusCode().value() + " body=" + body);
        assertNotNull(body, tag + ": body 不应为 null");
        assertFalse(body.contains("stackTrace"), tag + ": 泄露 stackTrace 字段");
        assertFalse(body.contains("lineNumber"), tag + ": 泄露 lineNumber");
        assertFalse(body.contains(".java"), tag + ": 泄露文件名/源码文件");
        assertFalse(body.contains("io.modelcontextprotocol"), tag + ": 泄露内部包名");
        assertFalse(body.contains("com.yansheng"), tag + ": 泄露本项目类名");
        assertFalse(body.contains("\tat "), tag + ": 泄露栈帧");
    }

    @Test
    void noTokenNoSession_shouldNotLeakStack() {
        ResponseEntity<String> resp = post(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        assertEquals(400, resp.getStatusCode().value(), "无 session 的 POST 应返回 400");
        assertNoInternalLeak("no-token", resp);
    }

    @Test
    void invalidSession_shouldNotLeakStack() {
        ResponseEntity<String> resp = post("invalid-session-123",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        assertEquals(404, resp.getStatusCode().value(), "无效 session 应返回 404");
        assertNoInternalLeak("invalid-session", resp);
    }

    @Test
    void malformedSession_shouldNotLeakStack() {
        ResponseEntity<String> resp = post("%%%not-a-session%%%",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        assertEquals(404, resp.getStatusCode().value(), "畸形 session 应返回 404");
        assertNoInternalLeak("malformed-session", resp);
    }

    @Test
    void normalInitialize_shouldSucceedAndExposeSessionId() {
        ResponseEntity<String> resp = post(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"b119-test\",\"version\":\"1.0\"}}}");
        System.out.println("[B-119][initialize] status=" + resp.getStatusCode().value()
                + " sessionId=" + resp.getHeaders().getFirst("mcp-session-id")
                + " body=" + resp.getBody());
        assertEquals(200, resp.getStatusCode().value(), "正常 initialize 应返回 200");
        assertNotNull(resp.getHeaders().getFirst("mcp-session-id"), "initialize 应返回 mcp-session-id 头");
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("\"result\""), "initialize 响应应含 result");
    }

    @Test
    void normalToolsList_afterInitialize_shouldWork() {
        ResponseEntity<String> init = post(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"b119-test\",\"version\":\"1.0\"}}}");
        assertEquals(200, init.getStatusCode().value(), "initialize 应 200");
        String sid = init.getHeaders().getFirst("mcp-session-id");
        assertNotNull(sid, "initialize 应返回 mcp-session-id");

        ResponseEntity<String> resp = post(sid,
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        System.out.println("[B-119][tools/list] status=" + resp.getStatusCode().value() + " body=" + resp.getBody());
        assertEquals(200, resp.getStatusCode().value(), "tools/list 应 200");
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("knowledge_search"), "tools/list 应返回已注册工具");
        assertFalse(resp.getBody().contains("stackTrace"), "tools/list 不应含堆栈");
    }

    /** 硬约束守卫:普通 @RestController 的畸形 JSON 仍走 GlobalExceptionHandler → 200 + 系统异常 */
    @Test
    void normalApiMalformedJson_stillReturnsSanitizedResult() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.exchange(url("/api/user/login"), HttpMethod.POST,
                new HttpEntity<>("{not-json", headers), String.class);
        assertNoInternalLeak("normal-api-bad-json", resp);
        assertEquals(200, resp.getStatusCode().value(), "普通 API 畸形 JSON 应 200");
        assertTrue(resp.getBody().contains("系统异常，请稍后重试"), "普通 API 畸形 JSON 应返回统一脱敏文案");
    }
}