package com.example.bookglebookgleserver.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

//JWT 발급 및 추출
@Service
public class JwtService {
    private final String ACCESS_SECRET = "your-access-token-secret-key-should-be-very-long-at-least-32-characters";
    private final String REFRESH_SECRET = "your-refresh-token-secret-key-should-be-very-long-at-least-32-characters";

    private final long ACCESS_TOKEN_EXPIRATION = 1000 * 60 * 60; // 1시간
    private final long REFRESH_TOKEN_EXPIRATION = 1000 * 60 * 60 * 24 * 7; // 7일


    // Access Token 생성
    public String createAccessToken(String email){
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String email){
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION))
                .signWith(Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    //Access Token에서 이메일 추출
    public String extractEmailFromAccessToken(String token){
        return extractClaims(token, ACCESS_SECRET).getSubject();
    }

    // Refresh Token에서 이메일 추출
    public String extractEmailFromRefreshToken(String token){
        return extractClaims(token, REFRESH_SECRET).getSubject();
    }

    // Access Token 유효성 검사
    public boolean isValidAccessToken(String token) {
        try {
            Claims claims = extractClaims(token, ACCESS_SECRET);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    // Refresh Token 유효성 검사
    public boolean isValidRefreshToken(String token) {
        try {
            Claims claims = extractClaims(token, REFRESH_SECRET);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractClaims(String token, String secret) {
        return Jwts.parserBuilder()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 기존 메서드 호환성을 위해 유지
    @Deprecated
    public String createToken(String email) {
        return createAccessToken(email);
    }

    @Deprecated
    public String extractEmail(String token) {
        return extractEmailFromAccessToken(token);
    }

    public long getRemainingExpiration(String token, boolean isRefreshToken) {
        String secret = isRefreshToken ? REFRESH_SECRET : ACCESS_SECRET;
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();
        long exp = claims.getExpiration().getTime();
        long now = System.currentTimeMillis();
        return Math.max((exp - now) / 1000, 0);
    }

}
