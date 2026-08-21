package com.yansheng.aiknowledgebase.config;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.utils.JwtUtil;
import com.yansheng.aiknowledgebase.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.yansheng.aiknowledgebase.config.SecurityConfig.WHITE_LIST;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 白名单:除 MCP 端点外直接放行
        if (WHITE_LIST.contains(uri)) {
            // MCP 端点:token 可选——带了就解析并设置用户上下文(工具按用户隔离),没带则放行(工具层拒绝业务数据)
            if (uri.endsWith("/api/mcp-endpoint") && tryParseToken(request)) {
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    UserContext.remove();
                }
            } else {
                filterChain.doFilter(request, response);
            }
            return;
        }

        // 非白名单:必须携带有效 token
        if (!tryParseToken(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.remove();
        }
    }

    /** 解析 Authorization: Bearer xxx 并设置 UserContext;成功返回 true,失败返回 false(不写响应) */
    private boolean tryParseToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return false;
        }
        String jwt = token.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(jwt);
            Long id = claims.get("id", Long.class);
            String username = claims.get("username", String.class);
            if (id == null || username == null) {
                return false;
            }
            UserEntity user = new UserEntity();
            user.setId(id);
            user.setUsername(username);
            UserContext.set(user);
            return true;
        } catch (Exception e) {
            // 令牌无效:不打印 token,避免敏感信息泄露
            return false;
        }
    }
}
