package com.example.bookglebookgleserver.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void encode1234() {
        String rawPassword = "1234";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        System.out.println("🔐 1234의 인코딩 값: " + encodedPassword);
    }
}
