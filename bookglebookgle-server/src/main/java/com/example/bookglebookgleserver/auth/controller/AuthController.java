package com.example.bookglebookgleserver.auth.controller;


import com.example.bookglebookgleserver.auth.dto.JwtResponse;
import com.example.bookglebookgleserver.auth.dto.LoginRequest;
import com.example.bookglebookgleserver.auth.dto.RefreshRequest;
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
            description = "이메일과 비밀번호를 받아 Access Token과 Refresh Token을 반환합니다."
    )
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request){
        System.out.println("🎯 AuthController에 요청 도달!");
        System.out.println("📧 받은 이메일: " + request.getEmail());
        System.out.println("🔑 받은 비밀번호: " + request.getPassword());

        try {
            JwtResponse response = authService.login(request.getEmail(), request.getPassword());
            System.out.println("✅ 토큰 생성 성공");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("💥 예외 발생: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(null);
        }
    }


    @Operation(
            summary = "토큰 갱신",
            description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다."
    )
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody RefreshRequest request){
        System.out.println("토큰 갱신 요청");

        try {
            JwtResponse response = authService.refreshToken(request.getRefreshToken());
            System.out.println("토큰 갱신 성공");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("큰 갱신 실패: " + e.getMessage());
            return ResponseEntity.status(401).body(null);
        }
    }

    @Operation(
            summary = "토큰 검증 테스트",
            description = "Access Token이 유효한지 확인하는 테스트 API"
    )
    @GetMapping("/verify")
    public ResponseEntity<String> verifyToken(@RequestHeader("Authorization") String authHeader){
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = authService.verifyToken(token);
            return ResponseEntity.ok("토큰이 유효합니다. 사용자: " + email);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("유효하지 않은 토큰입니다: " + e.getMessage());
        }
    }
}