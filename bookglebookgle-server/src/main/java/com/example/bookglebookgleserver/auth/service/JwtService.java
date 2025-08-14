package com.example.bookglebookgleserver.auth.service;

import com.example.bookglebookgleserver.auth.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

//JWT 발급 및 추출
@Service
public class JwtService {
    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessTtlMillis;   // 30분 = 1_800_000
    private final long refreshTtlMillis;  //  14일 = 1_209_600_000

    public JwtService(
            @Value("${jwt.accessSecret}") String accessSecret,
            @Value("${jwt.refreshSecret}") String refreshSecret,
            @Value("${jwt.accessTtlMillis}") long accessTtlMillis,
            @Value("${jwt.refreshTtlMillis}") long refreshTtlMillis
    ) {
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = accessTtlMillis;
        this.refreshTtlMillis = refreshTtlMillis;
    }

    // Access Token 생성
    public String createAccessToken(String email){
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTtlMillis))
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String email){
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTtlMillis))
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();
    }

    //Access Token에서 이메일 추출
    public String extractEmailFromAccessToken(String token){

        return parseClaims(token, accessKey).getSubject();
    }

    // Refresh Token에서 이메일 추출
    public String extractEmailFromRefreshToken(String token){
        return parseClaims(token, refreshKey).getSubject();
    }

    // Access Token 유효성 검사
    public boolean isValidAccessToken(String token) {
        try {
            parseClaims(token, accessKey);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Refresh Token 유효성 검사
    public boolean isValidRefreshToken(String token) {
        try {
            parseClaims(token, refreshKey);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }


    private Claims parseClaims(String token, SecretKey key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
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
        try {
            Claims claims = isRefreshToken
                    ? parseClaims(token, refreshKey)
                    : parseClaims(token, accessKey);
            long exp = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            return Math.max((exp - now) / 1000, 0);
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    /** 만료/서명 오류를 명확히 구분해 던지는 검증 메서드 */
    public void validateAccessTokenOrThrow(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(accessKey)
                    .build()
                    .parseClaimsJws(token);
        } catch (ExpiredJwtException e) {
            throw new com.example.bookglebookgleserver.auth.exception.TokenExpiredException("Access token expired", e);
        } catch (JwtException e) {
            throw new com.example.bookglebookgleserver.auth.exception.InvalidTokenException("Invalid access token", e);
        }
    }

}
