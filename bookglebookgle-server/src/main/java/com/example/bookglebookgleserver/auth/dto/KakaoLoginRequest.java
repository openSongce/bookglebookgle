package com.example.bookglebookgleserver.auth.dto;

public class KakaoLoginRequest {
    private String accessToken;

    public String getAccessToken() {
        System.out.println("💬 accessToken 값 들어옴: " + accessToken);
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        System.out.println("💬 accessToken setter 호출됨: " + accessToken);
        this.accessToken = accessToken;
    }
}
