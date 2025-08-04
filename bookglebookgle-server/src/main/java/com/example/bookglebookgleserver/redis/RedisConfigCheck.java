package com.example.bookglebookgleserver.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RedisConfigCheck {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @PostConstruct
    public void printRedisHost() {
        System.out.println("✅ 실제로 읽는 spring.redis.host: " + redisHost);
    }
}
