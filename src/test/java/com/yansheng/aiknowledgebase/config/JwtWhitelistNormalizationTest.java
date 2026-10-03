package com.yansheng.aiknowledgebase.config;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.utils.JwtUtil;
import com.yansheng.aiknowledgebase.utils.UserContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * B-121 回归:JWT 白名单判定归一化。
 * /api//user/login 折叠重复斜杠后显式命中白名单;尾斜杠同样命中;
 * %2F 编码斜杠不得被归一化(fail-closed);非白名单匿名访问仍 401。
 */
class JwtWhitelistNormalizationTest {

    private static final JwtUtil JWT_UTIL =
            new JwtUtil("YanshengAiknowledgeJwtSecretKey2026TestSecret");

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(JWT_UTIL);

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void doubleSlashLoginWithoutTokenPasses() throws Exception {
        MockHttpServletRequest request = request("/api//user/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest(), "归一化后应命中白名单并放行");
        assertNotEquals(401, response.getStatus(), "白名单端点不应返回 401");
    }

    @Test
    void trailingSlashLoginWithoutTokenPasses() throws Exception {
        MockHttpServletRequest request = request("/api/user/login/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest(), "去尾斜杠后应命中白名单并放行");
        assertNotEquals(401, response.getStatus());
    }

    @Test
    void encodedSlashIsNotNormalizedAndFailsClosed() throws Exception {
        MockHttpServletRequest request = request("/api%2Fuser/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus(), "编码斜杠不得当分隔符,必须 fail-closed");
        assertNull(chain.getRequest(), "未鉴权不得进入过滤链");
    }

    @Test
    void nonWhitelistAnonymousStillUnauthorized() throws Exception {
        MockHttpServletRequest request = request("/api/file/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus(), "非白名单端点匿名访问仍应 401");
        assertNull(chain.getRequest());
    }

    @Test
    void mcpWhitelistWithValidTokenPassesAndManagesContext() throws Exception {
        String token = JWT_UTIL.generateToken(7L, "mcpuser");
        MockHttpServletRequest request = request("/api/mcp-endpoint");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<UserEntity> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(UserContext.get());

        filter.doFilterInternal(request, response, chain);

        assertNotNull(duringChain.get(), "链执行期间 UserContext 应已设置");
        assertEquals("mcpuser", duringChain.get().getUsername());
        assertNull(UserContext.get(), "filter 结束后 UserContext 应被清理");
    }

    @Test
    void nonWhitelistWithValidTokenPassesAndClearsContext() throws Exception {
        String token = JWT_UTIL.generateToken(9L, "bob");
        MockHttpServletRequest request = request("/api/file/1");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<UserEntity> duringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringChain.set(UserContext.get());

        filter.doFilterInternal(request, response, chain);

        assertEquals("bob", duringChain.get().getUsername());
        assertNull(UserContext.get());
    }
}
