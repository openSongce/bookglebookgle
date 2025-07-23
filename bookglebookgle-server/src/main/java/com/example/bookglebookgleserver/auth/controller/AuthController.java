package com.example.bookglebookgleserver.auth.controller;


import com.example.bookglebookgleserver.auth.dto.JwtResponse;
import com.example.bookglebookgleserver.auth.dto.LoginRequest;
import com.example.bookglebookgleserver.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;


    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 받아 JWT 토큰을 반환합니다."
    )
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request){
        System.out.println("🎯 AuthController에 요청 도달!");
        System.out.println("📧 받은 이메일: " + request.getEmail());
        System.out.println("🔑 받은 비밀번호: " + request.getPassword());

        try {
            String token = authService.login(request.getEmail(), request.getPassword());
            System.out.println("✅ 토큰 생성 성공: " + token);
            return ResponseEntity.ok(new JwtResponse(token));
        } catch (Exception e) {
            System.out.println("💥 예외 발생: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(null);
        }
    }
}