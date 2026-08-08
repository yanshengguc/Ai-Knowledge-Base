package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;


public class JwtUtilTest {

    @Test
    public void testJwt(){

        String token = JwtUtil.generateToken(1L,"yan");

        System.out.println(token);

        Claims claims = JwtUtil.parseToken(token);

        System.out.println(claims.get("username"));
    }
}