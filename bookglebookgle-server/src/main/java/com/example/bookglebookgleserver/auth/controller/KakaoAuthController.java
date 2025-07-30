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
        String accessToken = request.getAccessToken();
        JsonNode userInfo = kakaoOAuthService.getUserInfo(accessToken);

        String kakaoId = userInfo.path("id").asText(); // 고유 ID
        String nicknameRaw = userInfo.path("properties").path("nickname").asText("카카오사용자");
        String profileImage = userInfo.path("properties").path("profile_image").asText(null); // null 허용

        String nickname = authService.generateUniqueNickname(nicknameRaw);

        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .nickname(nickname)
                        .profileImageUrl(profileImage)
                        .provider("kakao")
                        .build()));

        String jwtAccessToken = jwtService.createAccessToken(user.getEmailOrDefault());
        String jwtRefreshToken = jwtService.createRefreshToken(user.getEmailOrDefault());

        return ResponseEntity.ok(new JwtResponse(jwtAccessToken, jwtRefreshToken));
    }

}
