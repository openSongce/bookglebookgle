package com.example.bookglebookgleserver.global.util;

import com.example.bookglebookgleserver.global.exception.AuthException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthUtil {

    public static String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserDetails userDetails)) {
            throw new AuthException("인증된 사용자를 찾을 수 없습니다.");
        }

        return userDetails.getUsername();
    }
}
