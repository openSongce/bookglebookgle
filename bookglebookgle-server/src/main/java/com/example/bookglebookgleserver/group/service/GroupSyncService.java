package com.example.bookglebookgleserver.group.service;

import com.example.bookglebookgleserver.global.exception.InternalServerErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupSyncService {

    private final StringRedisTemplate redisTemplate;

    // 리더 페이지를 Redis에 저장
    public void updateLeaderPage(Long groupId, int page) {
        String key = "group:" + groupId + ":leaderPage";
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(page));
        } catch (Exception e) {
            // 장애 로깅, 모니터링
            log.error("Redis 저장 실패: {}", e.getMessage(), e);
            throw new InternalServerErrorException("동기화 서버 오류");
        }
    }


    // 리더 페이지를 Redis에서 조회
    public int getLeaderPage(Long groupId) {
        String key = "group:" + groupId + ":leaderPage";
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : 1; // 없으면 1페이지 반환
    }
}
