package com.example.bookglebookgleserver.auth.controller;


import com.example.bookglebookgleserver.auth.dto.JwtResponse;
import com.example.bookglebookgleserver.auth.service.AuthService;
import com.example.bookglebookgleserver.auth.service.JwtService;
import com.example.bookglebookgleserver.auth.service.KakaoOAuthService;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class KakaoAuthController {
    private final KakaoOAuthService kakaoOAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Operation(
            summary = "카카오 소셜 로그인",
            description = "카카오 accessToken으로 사용자 정보를 가져와 JWT를 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 또는 회원가입 성공, Access Token과 Refresh Token 반환"),
            @ApiResponse(responseCode = "400", description = "accessToken이 유효하지 않음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/oauth/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestBody String accessToken) {
        JsonNode userInfo = kakaoOAuthService.getUserInfo(accessToken);

        String email = userInfo.get("kakao_account").get("email").asText();
        String nicknameRaw = userInfo.get("properties").get("nickname").asText();
        String nickname = authService.generateUniqueNickname(nicknameRaw);

        // 회원가입 or 로그인
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .nickname(nickname)
                        .provider("kakao")
                        .build()));

        String jwtAccessToken = jwtService.createAccessToken(user.getEmail());
        String jwtRefreshToken = jwtService.createRefreshToken(user.getEmail());

        return ResponseEntity.ok(new JwtResponse(jwtAccessToken, jwtRefreshToken));
    }
}
