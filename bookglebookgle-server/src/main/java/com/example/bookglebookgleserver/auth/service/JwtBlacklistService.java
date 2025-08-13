package com.example.bookglebookgleserver.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private final StringRedisTemplate redisTemplate;

    // 토큰을 블랙리스트에 등록 (만료까지 남은 시간도 같이 저장)
    public void blacklistToken(String jti, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("bl:"+jti, "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("[JWT] Blacklist 실패(연결/인증 문제 가능): {}", ex.toString());
            // 예외 전파하지 않음: 로그아웃은 항상 200으로
        }
    }

    // 토큰이 블랙리스트에 있는지 체크
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklisted:" + token));
    }
}
