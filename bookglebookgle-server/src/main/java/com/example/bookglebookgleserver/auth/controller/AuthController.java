package com.example.bookglebookgleserver.auth.controller;


import com.example.bookglebookgleserver.auth.dto.JwtResponse;
import com.example.bookglebookgleserver.auth.dto.LoginRequest;
import com.example.bookglebookgleserver.auth.dto.RefreshRequest;
import com.example.bookglebookgleserver.auth.dto.SignupRequest;
import com.example.bookglebookgleserver.auth.service.*;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    private final UserRepository userRepository;
    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;
    private final JwtBlacklistService jwtBlacklistService;


    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 받아 Access Token과 Refresh Token을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request){

        try {
            JwtResponse response = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("예외 발생: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body(null);
        }
    }


    @Operation(
            summary = "토큰 갱신",
            description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody RefreshRequest request){
        try {
            JwtResponse response = authService.refreshToken(request.getRefreshToken());
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 유효"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 토큰")
    })
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

    @Operation(summary = "이메일 중복 검사 및 인증코드 발송", description = "이메일이 중복되지 않으면 인증코드를 발송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 코드 발송 성공"),
            @ApiResponse(responseCode = "409", description = "이미 등록된 이메일")
    })
    @PostMapping("/check/email")
    public ResponseEntity<String> checkEmailAndSendCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (authService.isEmailDuplicated(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 등록된 이메일입니다.");
        }
        authService.sendVerificationCode(email);
        return ResponseEntity.ok("인증 코드가 이메일로 발송되었습니다.");
    }

    @Operation(summary = "닉네임 중복 확인", description = "입력한 닉네임이 이미 존재하는지 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "닉네임 사용 가능"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임")
    })
    @GetMapping("/check/nickname")
    public ResponseEntity<String> checkNickname(@RequestParam String nickname) {
        log.debug("닉네임 중복 체크 요청 :[{}]", nickname);
        boolean exists = authService.isNicknameDuplicated(nickname);
        if (exists) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 사용 중인 닉네임입니다.");
        }
        return ResponseEntity.ok("사용 가능한 닉네임입니다.");
    }


    @Operation(
            summary = "이메일 회원가입",
            description = "이메일, 비밀번호, 닉네임 등의 정보를 기반으로 회원가입을 진행합니다.<br>" +
                    "사전에 이메일 인증이 완료되어야 합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "이메일 인증이 완료되지 않음"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 이메일"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/signup/email")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        try {
            authService.signup(request);
            return ResponseEntity.ok("회원가입 완료");
        } catch (Exception e) {
            System.err.println(" 회원가입 실패: " + e.getMessage());
            e.printStackTrace();

            // 구체적인 에러 메시지 반환
            if (e.getMessage().contains("이미 존재")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 존재하는 이메일입니다.");
            } else if (e.getMessage().contains("인증")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("이메일 인증이 필요합니다.");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("회원가입 처리 중 오류가 발생했습니다: " + e.getMessage());
            }
        }
    }


    @Operation(
            summary = "구글 소셜 로그인",
            description = "프론트엔드에서 전달한 Google ID Token을 검증하여 회원가입 또는 로그인을 수행하고 JWT 토큰을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 또는 회원가입 성공, Access Token과 Refresh Token 반환"),
            @ApiResponse(responseCode = "400", description = "ID Token이 유효하지 않음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/oauth/google")
    public ResponseEntity<?> googleCallback(@RequestBody Map<String, String> body) {
        String idToken = body.get("idToken");

        // 1. ID 토큰 검증
        GoogleIdToken.Payload payload = GoogleTokenVerifier.verify(idToken);
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        // 2. 닉네임 중복 처리
        String nickname = authService.generateUniqueNickname(name);

        // 3. 회원가입 or 기존 로그인
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .nickname(nickname)
                        .provider("google")
                        .build()));

        // 4. JWT 발급
        String accessToken = jwtService.createAccessToken(user.getEmail());
        String refreshToken= jwtService.createRefreshToken(user.getEmail());


        refreshTokenService.saveRefreshToken(user.getEmail(), refreshToken);


        JwtResponse response = new JwtResponse(
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getId(),            // userId 추가
                user.getAvgRating()      // avgRating 추가
        );
        return ResponseEntity.ok(response);

    }

    @Operation(
            summary = "로그아웃",
            description = """
        <b>AccessToken</b>은 Authorization 헤더에,<br>
        <b>RefreshToken</b>은 <code>body</code>에 JSON으로 전달합니다.<br><br>
        <b>모바일(Android/iOS) 환경 전용!</b><br>
        - 로그아웃 시 두 토큰을 모두 서버에서 무효화(블랙리스트 등록)합니다.<br>
        - 로그아웃 후 이 토큰들로 더 이상 인증/재발급 불가
    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공, 두 토큰이 모두 무효화됨"),
            @ApiResponse(responseCode = "500", description = "로그아웃 처리 중 서버 오류")
    })
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String authHeader,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "body에 RefreshToken을 JSON 형태로 전달. ex) { \"refreshToken\": \"...\" }",
                    required = false,
                    content = @Content(
                            schema = @Schema(
                                    example = "{\"refreshToken\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\"}"
                            )
                    )
            )
            @RequestBody(required = false) Map<String, String> body
    ) {
        try {
            String accessToken = authHeader.replace("Bearer ", "");
            long accessExpireSec = jwtService.getRemainingExpiration(accessToken, false);
            jwtBlacklistService.blacklistToken(accessToken, accessExpireSec);

            String refreshToken = (body != null) ? body.get("refreshToken") : null;
            if (refreshToken != null && !refreshToken.isBlank()) {
                long refreshExpireSec = jwtService.getRemainingExpiration(refreshToken, true);
                jwtBlacklistService.blacklistToken(refreshToken, refreshExpireSec);
            }

            return ResponseEntity.ok("로그아웃 완료");
        } catch (Exception e) {
            log.error("로그아웃 실패: ", e);
            return ResponseEntity.status(500).body("로그아웃 처리 중 오류");
        }
    }
}