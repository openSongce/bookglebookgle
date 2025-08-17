package com.example.bookglebookgleserver.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;



@Service
@RequiredArgsConstructor
public class ActiveChatRegistry {
    private final StringRedisTemplate redis;

    private String key(Long roomId) { return "chat:room:%d:members".formatted(roomId); }

    public void enter(Long roomId, Long userId) {
        redis.opsForSet().add(key(roomId), String.valueOf(userId));
        // 선택: heartbeat용 TTL (별도 키로 관리 권장)
        redis.expire(key(roomId), Duration.ofMinutes(30));
    }

    public void leave(Long roomId, Long userId) {
        redis.opsForSet().remove(key(roomId), String.valueOf(userId));
    }

    public Set<Long> getActiveUsers(Long roomId) {
        var raw = redis.opsForSet().members(key(roomId));
        if (raw == null) return Set.of();
        return raw.stream().map(Long::valueOf).collect(Collectors.toSet());
    }
}
