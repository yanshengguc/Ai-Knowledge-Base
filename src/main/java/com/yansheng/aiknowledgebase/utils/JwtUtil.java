package com.yansheng.aiknowledgebase.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

import static java.security.KeyRep.Type.SECRET;


public class JwtUtil {
    private static final long EXPIRE_TIME = 3600 * 1000;
    private static final String SECRET_KEY =  "YanshengAiknowledgeJwtSecretKey2026";
   public static String generateToken( Long id ,String username){
       JwtBuilder builder = Jwts.builder();
               builder.claim("username",username)
                       .claim("id", id);
       builder.expiration(new Date(System.currentTimeMillis()+EXPIRE_TIME));
       SecretKey key=Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
       builder.signWith(key);
       return builder.compact();

   }
   public  static Claims parseToken(String token) {
       SecretKey  key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
       JwtParser parser = Jwts.parser().verifyWith(key).build();
      Jws<Claims> jws = parser.parseSignedClaims(token);
      Claims claims = jws.getPayload();


       return claims;

   }


}
