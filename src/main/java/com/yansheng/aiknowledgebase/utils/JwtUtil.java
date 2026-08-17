package com.yansheng.aiknowledgebase.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 工具(安全加固 2026-08-17):
 * - 密钥从配置注入(jwt.secret-key),不再硬编码在源码——防源码泄露即伪造 token
 * - 密钥要求 >= 32 字节(HS256 最低 256bit)
 */
@Component
public class JwtUtil {

    private static final long EXPIRE_TIME = 3600 * 1000;

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret-key}") String secretKey) {
        if (secretKey == null || secretKey.getBytes().length < 32) {
            throw new IllegalStateException("jwt.secret-key 未配置或长度不足 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(Long id, String username) {
        return Jwts.builder()
                .claim("username", username)
                .claim("id", id)
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }
}
