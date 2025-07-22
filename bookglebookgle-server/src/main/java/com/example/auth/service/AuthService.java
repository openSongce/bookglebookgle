package com.example.auth.service;


import com.example.user.entity.User;
import com.example.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;




//로그인 로직
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    //sprnig security
    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtservice;

    public String login(String email, String password){
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("존재하지않는 사용자입니다"));

        if(!passwordEncoder.matches(password, user.getPassword())){
            throw new RuntimeException("비밀번호가 일치하지않습니다");
        }

        return jwtservice.createToken(user.getEmail());

    }
}
