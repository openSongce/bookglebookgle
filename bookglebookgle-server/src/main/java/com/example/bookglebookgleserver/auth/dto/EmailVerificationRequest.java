package com.example.bookglebookgleserver.auth.dto;


import lombok.Data;

@Data
public class EmailVerificationRequest {
    private String email;
    private String code;
}
