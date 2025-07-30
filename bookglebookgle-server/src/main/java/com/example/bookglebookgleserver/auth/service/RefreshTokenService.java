package com.example.bookglebookgleserver.auth.service;


import com.example.bookglebookgleserver.common.verification.entity.RefreshToken;
import com.example.bookglebookgleserver.common.verification.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    //나중에 Redis로 변경
//    private final Map<String,String> refreshTokenStore=new ConcurrentHashMap<>();

    private final RefreshTokenRepository refreshTokenRepository;



    public void saveRefreshToken(String email, String refreshToken) {
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .email(email)
                        .token(refreshToken)
                        .build()
        );
        System.out.println("Refresh Token 저장: " + email);
    }

    public boolean isValidRefreshToken(String email, String refreshToken) {
        return refreshTokenRepository.findById(email)
                .map(rt -> rt.getToken().equals(refreshToken))
                .orElse(false);
    }

    public void deleteRefreshToken(String email) {
        refreshTokenRepository.deleteById(email);
        System.out.println("🗑 Refresh Token 삭제: " + email);
    }



}
