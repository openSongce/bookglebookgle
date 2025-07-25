package com.example.bookglebookgleserver.auth.dto;


import lombok.Data;

@Data
public class SignupRequest {
    private String email;
    private String password;
    private String nickname;
    private String code;  // 인증코드
}
