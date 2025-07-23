package com.example.bookglebookgleserver.auth.service;


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

    private final JwtService jwtservice;

    public String login(String email, String password){

        System.out.println("📥 로그인 요청: " + email + " / " + password);
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("존재하지않는 사용자입니다"));

        System.out.println("✅ DB 사용자 조회 성공: " + user.getEmail());
        if(!passwordEncoder.matches(password, user.getPassword())){
            throw new RuntimeException("비밀번호가 일치하지않습니다");
        }
        System.out.println("🔓 비밀번호 일치, 토큰 발급");

        return jwtservice.createToken(user.getEmail());

    }
}
