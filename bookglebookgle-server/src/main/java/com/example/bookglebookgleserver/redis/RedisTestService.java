package com.example.bookglebookgleserver.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisTestService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void testRedis(){
        redisTemplate.opsForValue().set("hello", "bookglebookgle!");
        String value = redisTemplate.opsForValue().get("hello");
        System.out.println("Redis에서 읽은 값: " + value);
    }
}
