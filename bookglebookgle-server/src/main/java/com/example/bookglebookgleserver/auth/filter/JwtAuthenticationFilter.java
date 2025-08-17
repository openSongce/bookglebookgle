package com.example.bookglebookgleserver.auth.filter;

import com.example.bookglebookgleserver.auth.dto.ErrorResponse;
import com.example.bookglebookgleserver.auth.exception.InvalidTokenException;
import com.example.bookglebookgleserver.auth.exception.TokenExpiredException;
import com.example.bookglebookgleserver.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Swagger & 공개 경로 통과
        if (uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-resources")
                || uri.startsWith("/webjars")
                || uri.startsWith("/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더 확인
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("[JWT 필터] Authorization 헤더 없음 또는 형식 불일치: {}", authHeader);
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // 1) 유효성 검증 (만료/서명 오류 구분)
        try {
            jwtService.validateAccessTokenOrThrow(token);
        } catch (TokenExpiredException e) {
            write401(response, "ACCESS_TOKEN_EXPIRED", "Access token expired");
            return;
        } catch (InvalidTokenException e) {
            write401(response, "ACCESS_TOKEN_INVALID", "Invalid access token");
            return;
        }

        // 2) 컨텍스트 세팅
        String email = jwtService.extractEmailFromAccessToken(token);
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            log.info("[JWT] auth set: principalType={}, username={}, authorities={}",
                    userDetails.getClass().getName(),
                    userDetails.getUsername(),
                    userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }

    private void write401(HttpServletResponse res, String code, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        String body = new ObjectMapper().writeValueAsString(new ErrorResponse(code, message));
        res.getWriter().write(body);
    }
}