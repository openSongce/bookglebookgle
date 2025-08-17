package com.example.bookglebookgleserver.auth.service;


import com.example.bookglebookgleserver.common.verification.entity.RefreshToken;
import com.example.bookglebookgleserver.common.verification.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    //나중에 Redis로 변경
//    private final Map<String,String> refreshTokenStore=new ConcurrentHashMap<>();

    private final RefreshTokenRepository refreshTokenRepository;



    public void saveRefreshToken(String email, String refreshToken) {
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .email(email)
                        .token(refreshToken)
                        .build()
        );
    }

    public boolean isValidRefreshToken(String email, String refreshToken) {
        return refreshTokenRepository.findById(email)
                .map(rt -> rt.getToken().equals(refreshToken))
                .orElse(false);
    }

    public void deleteAllRefreshTokensByEmail(String email) {
        // 해당 사용자의 모든 RefreshToken 삭제
        refreshTokenRepository.deleteByEmail(email);
        log.info("사용자의 모든 RefreshToken 삭제 완료: {}", email);
    }

    public void deleteRefreshToken(String email) {
        refreshTokenRepository.deleteById(email);
    }

}
