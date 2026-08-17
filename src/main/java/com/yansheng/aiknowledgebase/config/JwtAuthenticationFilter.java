package com.yansheng.aiknowledgebase.config;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.utils.JwtUtil;
import com.yansheng.aiknowledgebase.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
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
        if (WHITE_LIST.contains(uri) ) {
filterChain.doFilter(request, response);
return;
        }
        String token = request.getHeader("Authorization");
if (token==null || !token.startsWith("Bearer ")) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return;
}
            String jwt = token.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(jwt);
                Long id = claims.get("id", Long.class);
                String username = claims.get("username", String.class);
if(id==null||username==null){
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return;
}
                UserEntity user = new UserEntity();
                user.setId(id);
                user.setUsername(username);
                UserContext.set(user);

                // 放行请求
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                // 令牌无效，返回401(不打印 token,避免敏感信息泄露)
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            } finally {
                UserContext.remove();
            }


        }
    }
