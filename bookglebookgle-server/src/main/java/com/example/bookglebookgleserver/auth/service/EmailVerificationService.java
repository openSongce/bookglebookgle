package com.example.bookglebookgleserver.auth.service;


import com.example.bookglebookgleserver.common.verification.entity.VerificationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import com.example.bookglebookgleserver.common.verification.repository.VerificationCodeRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {


    //redis사용코드
//    private final RedisTemplate<String, String> redisTemplate;
//
//    public boolean verifyCode(String email, String code) {
//        String savedCode = redisTemplate.opsForValue().get(email);
//        return code.equals(savedCode);
//    }
    private final VerificationCodeRepository verificationCodeRepository;

    public boolean verifyCode(String email, String code) {
        Optional<VerificationCode> latestCodeOpt =
                verificationCodeRepository.findTopByEmailOrderByCreatedAtDesc(email); // ✅ 여기 수정

        if (latestCodeOpt.isEmpty()) return false;

        VerificationCode latestCode = latestCodeOpt.get();

        return code.equals(latestCode.getCode()) &&
                latestCode.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5));
    }

}
