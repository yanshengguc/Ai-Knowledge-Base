package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A2 JWT 校验回归测试(修复假测试:原测试 0 断言,只打印)
 * 验证:生成可解析 / 篡改被拒 / 过期被拒 / 格式非法被拒
 */
class JwtUtilTest {

    private static final JwtUtil jwtUtil = new JwtUtil("YanshengAiknowledgeJwtSecretKey2026TestSecret");

    // 与 JwtUtil 相同的密钥,仅用于测试构造"过期 token"
    private static final String TEST_SECRET = "YanshengAiknowledgeJwtSecretKey2026TestSecret";

    @Test
    void testGenerateAndParseToken() {
        Long id = 42L;
        String username = "tester";
        String token = jwtUtil.generateToken(id, username);

        assertNotNull(token);
        assertFalse(token.isBlank(), "token 不应为空");
        // token 标准结构:header.payload.signature 三段
        assertEquals(3, token.split("\\.").length, "JWT 应为三段式");

        Claims claims = jwtUtil.parseToken(token);
        assertEquals(id, claims.get("id", Long.class), "解析出的 id 必须一致");
        assertEquals(username, claims.get("username", String.class), "解析出的 username 必须一致");
    }

    @Test
    void testTamperedTokenRejected() {
        String token = jwtUtil.generateToken(1L, "yan");
        // 篡改签名段首字符:首字符 6 位全部有效,且替换字符必须选不同 Base64 组——
        // 同组字符(索引低 2 位是 padding 位)解码后字节相同,篡改等于没改(原实现改末位即踩此坑,~6% 概率 flaky)
        String[] parts = token.split("\\.", 3);
        char first = parts[2].charAt(0);
        char replacement = (first >= 'A' && first <= 'D') ? 'E' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);

        assertThrows(JwtException.class, () -> jwtUtil.parseToken(tampered),
                "篡改 token 必须被拒绝,不能解析出任何身份");
    }

    @Test
    void testExpiredTokenRejected() {
        // 构造一个已过期的 token(用同一密钥签名)
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .claim("id", 1L)
                .claim("username", "yan")
                .expiration(new Date(System.currentTimeMillis() - 1000))  // 1 秒前过期
                .signWith(key)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parseToken(expiredToken),
                "过期 token 必须被拒绝");
    }

    @Test
    void testMalformedTokenRejected() {
        assertThrows(JwtException.class, () -> jwtUtil.parseToken("not-a-jwt"),
                "非法格式 token 必须被拒绝");
        // jjwt 对空/空串抛 IllegalArgumentException(同样被拒绝,不进业务层)
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.parseToken(""),
                "空 token 必须被拒绝");
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.parseToken(null),
                "null token 必须被拒绝");
    }
}
