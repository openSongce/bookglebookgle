package com.example.bookglebookgleserver.auth.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    //나중에 Redis로 변경
    private final Map<String,String> refreshTokenStore=new ConcurrentHashMap<>();

    public void saveRefreshToken(String email, String refreshToken){
        refreshTokenStore.put(email,refreshToken);
        System.out.println("Refresh Token 저장"+email);
    }

    public boolean isValidRefreshToken(String email, String refreshToken) {
        String storedToken = refreshTokenStore.get(email);
        return storedToken != null && storedToken.equals(refreshToken);
    }

    public void deleteRefreshToken(String email) {
        refreshTokenStore.remove(email);
        System.out.println("🗑 Refresh Token 삭제: " + email);
    }



}
