package com.example.bookglebookgleserver.auth.controller;


import com.example.bookglebookgleserver.auth.dto.JwtResponse;
import com.example.bookglebookgleserver.auth.dto.KakaoLoginRequest;
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
    public ResponseEntity<?> kakaoLogin(@RequestBody KakaoLoginRequest request) {

        try {

            String accessToken = request.getAccessToken();
            JsonNode userInfo = kakaoOAuthService.getUserInfo(accessToken);

            String kakaoId = userInfo.path("id").asText();
            String generatedEmail = "kakao_" + kakaoId + "@bookgle.com";

            String nicknameRaw = userInfo.path("properties").path("nickname").asText("카카오사용자");
            String profileImage = userInfo.path("properties").path("profile_image").asText(null);
            String nickname = authService.generateUniqueNickname(nicknameRaw);

            User user = userRepository.findByEmail(generatedEmail)
                    .orElseGet(() -> {
                        return userRepository.save(User.builder()
                                .email(generatedEmail)
                                .nickname(nickname)
                                .profileImageUrl(profileImage)
                                .provider("kakao")
                                .build());
                    });

            String jwtAccessToken = jwtService.createAccessToken(user.getEmail());
            String jwtRefreshToken = jwtService.createRefreshToken(user.getEmail());

            authService.saveRefreshToken(user.getEmail(), jwtRefreshToken);

            return ResponseEntity.ok(new JwtResponse(jwtAccessToken, jwtRefreshToken));
        } catch (Exception e) {
            e.printStackTrace(); // 콘솔에 전체 예외 로그 출력
            return ResponseEntity.status(500).body("카카오 로그인 실패: " + e.getMessage());
        }
    }

}
