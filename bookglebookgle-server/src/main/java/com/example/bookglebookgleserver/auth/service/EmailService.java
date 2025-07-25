package com.example.bookglebookgleserver.auth.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final RedisTemplate<String, String> redisTemplate;

    public boolean verifyCode(String email, String code) {
        String savedCode = redisTemplate.opsForValue().get(email);
        return code.equals(savedCode);
    }

}
