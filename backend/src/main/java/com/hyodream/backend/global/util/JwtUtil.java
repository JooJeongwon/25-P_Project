package com.hyodream.backend.global.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // 비밀키 (32글자 이상이어야 안전함)
    private static final String SECRET_KEY_STRING = "ThisIsAVeryLongSecretKeyForHyoDreamProjectSecurity123456";
    private final Key key = Keys.hmacShaKeyFor(SECRET_KEY_STRING.getBytes());

    // 유효시간 설정
    private final long ACCESS_TIME = 30 * 60 * 1000L; // 30분
    private final long REFRESH_TIME = 7 * 24 * 60 * 60 * 1000L; // 7일

    // 1. Access Token 생성 (짧은 거)
    public String createAccessToken(String username) {
        return createToken(username, ACCESS_TIME);
    }

    // 2. Refresh Token 생성 (긴 거)
    public String createRefreshToken(String username) {
        return createToken(username, REFRESH_TIME);
    }

    // 내부적으로 토큰 만드는 로직
    private String createToken(String username, long expireTime) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expireTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 3. 토큰에서 아이디 꺼내기
    public String getUsername(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // 4. 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}