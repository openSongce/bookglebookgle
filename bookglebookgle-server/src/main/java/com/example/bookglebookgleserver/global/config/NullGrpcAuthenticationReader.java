package com.example.bookglebookgleserver.global.config;

import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import org.springframework.security.core.Authentication;

// 직접 Null 객체 구현
public class NullGrpcAuthenticationReader implements GrpcAuthenticationReader {
    @Override
    public Authentication readAuthentication(ServerCall<?, ?> call, Metadata headers) {
        return null; // 항상 인증 정보 없음
    }
}
