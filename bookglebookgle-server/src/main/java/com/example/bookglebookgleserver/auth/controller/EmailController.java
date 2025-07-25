package com.example.bookglebookgleserver.auth.controller;


import com.example.bookglebookgleserver.auth.dto.EmailVerificationRequest;
import com.example.bookglebookgleserver.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;

    @PostMapping("/verify")
    public ResponseEntity<Boolean> verifyCode(@RequestBody EmailVerificationRequest request) {
        boolean result = emailService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(result);
    }

}
