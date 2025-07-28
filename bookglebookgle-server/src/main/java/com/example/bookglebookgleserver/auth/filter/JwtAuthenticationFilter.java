package com.example.bookglebookgleserver.auth.filter;

import com.example.bookglebookgleserver.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Qualifier("customUserDetailsService")
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        System.out.println("[JWT 필터] 요청 URI: " + uri);

        // ✅ Swagger 관련 경로는 JWT 필터 무시 (여기 추가!)
        if (uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-resources") || uri.startsWith("/webjars")) {
            System.out.println("[JWT 필터] Swagger 요청 -> 필터 스킵");
            filterChain.doFilter(request, response);
            return;
        }

        if (uri.startsWith("/auth")) {
            System.out.println("[JWT 필터] 인증 예외 경로로 필터 스킵");
            filterChain.doFilter(request, response);
            return;
        }

        // 이하 기존 로직 유지
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("[JWT 필터] Authorization 헤더 없음 또는 형식 불일치");
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        System.out.println("[JWT 필터] 추출된 토큰: " + token);

        String email = jwtService.extractEmailFromAccessToken(token);
        System.out.println("[JWT 필터] 추출된 이메일: " + email);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            System.out.println("[JWT 필터] 유저 로드 성공: " + userDetails.getUsername());

            if (jwtService.isValidAccessToken(token)) {
                System.out.println("[JWT 필터] 토큰 유효성 검사 통과");

                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                System.out.println("[JWT 필터] 인증 정보 등록 완료");
            } else {
                System.out.println("[JWT 필터] 토큰 유효하지 않음");
            }
        } else {
            System.out.println("[JWT 필터] 이메일이 없거나 이미 인증된 요청");
        }

        filterChain.doFilter(request, response);
    }


}
