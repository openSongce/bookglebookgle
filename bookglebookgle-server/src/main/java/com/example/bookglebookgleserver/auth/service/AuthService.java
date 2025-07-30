package com.example.bookglebookgleserver.auth.service;


import com.example.bookglebookgleserver.auth.dto.JwtResponse;
import com.example.bookglebookgleserver.auth.dto.SignupRequest;
import com.example.bookglebookgleserver.common.util.EmailSender;
import com.example.bookglebookgleserver.common.verification.entity.VerificationCode;
import com.example.bookglebookgleserver.common.verification.repository.VerificationCodeRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;


//로그인 로직
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    //sprnig security
    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    private final RedisTemplate<String, String> redisTemplate;


    //email인증
    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailSender emailSender;

    public boolean isEmailDuplicated(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public void sendVerificationCode(String email) {
        String code = generateCode();
        VerificationCode verificationCode = VerificationCode.builder()
                .email(email)
                .code(code)
                .createdAt(LocalDateTime.now())
                .build();
        verificationCodeRepository.save(verificationCode);
        emailSender.send(email, "북글북글 인증코드", getEmailBody(code));
    }


    private String generateCode() {
        return String.valueOf((int)(Math.random() * 900000) + 100000);  // 6자리 숫자
    }

    private String getEmailBody(String code) {
        return "<h2>[북글북글 이메일 인증]</h2>"
                + "<p>아래 인증코드를 입력해주세요:</p>"
                + "<h3 style='color:blue;'>" + code + "</h3>"
                + "<p>유효 시간: 5분</p>";
    }


    public JwtResponse login(String email, String password){

        System.out.println("로그인 요청: " + email + " / " + password);
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("존재하지않는 사용자입니다"));

        System.out.println("DB 사용자 조회 성공: " + user.getEmail());
        if(!passwordEncoder.matches(password, user.getPassword())){
            throw new RuntimeException("비밀번호가 일치하지않습니다");
        }
        System.out.println("비밀번호 일치, 토큰 발급");

        String accessToken = jwtService.createAccessToken(user.getEmail());
        String refreshToken = jwtService.createRefreshToken(user.getEmail());


        // Refresh Token 저장 (나중애 DB 또는 Redis 저장으로 변경)
        refreshTokenService.saveRefreshToken(user.getEmail(), refreshToken);


        return new JwtResponse(accessToken,
                refreshToken,
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl());

    }

    public JwtResponse refreshToken(String refreshToken) {
        System.out.println(" 토큰 갱신 시작");

        // Refresh Token 검증
        if (!jwtService.isValidRefreshToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다");
        }

        String email = jwtService.extractEmailFromRefreshToken(refreshToken);

        // DB에 저장된 Refresh Token과 비교
        if (!refreshTokenService.isValidRefreshToken(email, refreshToken)) {
            throw new RuntimeException("만료되거나 유효하지 않은 Refresh Token입니다");
        }

        // 새로운 토큰들 생성
        String newAccessToken = jwtService.createAccessToken(email);
        String newRefreshToken = jwtService.createRefreshToken(email);

        // 새로운 Refresh Token 저장
        refreshTokenService.saveRefreshToken(email, newRefreshToken);

        System.out.println("토큰 갱신 완료");
        return new JwtResponse(newAccessToken, newRefreshToken);
    }


    public String verifyToken(String token) {
        if (!jwtService.isValidAccessToken(token)) {
            throw new RuntimeException("유효하지 않은 Access Token입니다");
        }
        return jwtService.extractEmailFromAccessToken(token);
    }

    public void signup(SignupRequest request){
        // 비밀번호 암호화 및 저장
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        userRepository.save(user);
//        redisTemplate.delete(request.getEmail()); // 인증코드 삭제

    }

    public boolean isNicknameDuplicated(String nickname) {
        return userRepository.existsByNickname(nickname);
    }


    public String generateUniqueNickname(String base) {
        String nickname = base;
        int suffix = 1;
        while (userRepository.existsByNickname(nickname)) {
            nickname = base + suffix++;
        }
        return nickname;
    }




}
