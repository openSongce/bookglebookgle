package com.example.bookglebookgleserver.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private final StringRedisTemplate redisTemplate;

    // 토큰을 블랙리스트에 등록 (만료까지 남은 시간도 같이 저장)
    public void blacklistToken(String token, long expireSeconds) {
        redisTemplate.opsForValue().set("blacklisted:" + token, "1", expireSeconds, TimeUnit.SECONDS);
    }

    // 토큰이 블랙리스트에 있는지 체크
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklisted:" + token));
    }
}
