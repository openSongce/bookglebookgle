package com.example.bookglebookgleserver.auth.service;


import com.example.bookglebookgleserver.auth.dto.JwtResponse;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;


//로그인 로직
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    //sprnig security
    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public JwtResponse login(String email, String password){

        System.out.println("📥 로그인 요청: " + email + " / " + password);
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("존재하지않는 사용자입니다"));

        System.out.println("✅ DB 사용자 조회 성공: " + user.getEmail());
        if(!passwordEncoder.matches(password, user.getPassword())){
            throw new RuntimeException("비밀번호가 일치하지않습니다");
        }
        System.out.println("🔓 비밀번호 일치, 토큰 발급");

        String accessToken = jwtService.createAccessToken(user.getEmail());
        String refreshToken = jwtService.createRefreshToken(user.getEmail());


        // Refresh Token 저장 (나중애 DB 또는 Redis 저장으로 변경)
        refreshTokenService.saveRefreshToken(user.getEmail(), refreshToken);


        return new JwtResponse(accessToken, refreshToken);

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





}
