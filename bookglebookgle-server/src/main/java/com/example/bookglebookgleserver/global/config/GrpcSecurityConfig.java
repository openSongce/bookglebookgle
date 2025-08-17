package com.example.bookglebookgleserver.global.config;

import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcSecurityConfig {

    @Bean
    public GrpcAuthenticationReader grpcAuthenticationReader() {
        // 인증 기능을 사용하지 않을 것이므로 Null 리더를 사용
        return new NullGrpcAuthenticationReader();
    }
}
