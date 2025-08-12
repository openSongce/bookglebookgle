package com.example.bookglebookgleserver.auth.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private final String code;     // 예: ACCESS_TOKEN_EXPIRED
    private final String message;  // 예: Access token expired
}
