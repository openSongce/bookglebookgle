package com.example.bookglebookgleserver.pdf.service;

import com.example.bookglebookgleserver.global.exception.AuthException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.repository.PdfFileRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j // ✅ 로깅을 위한 Lombok 어노테이션
public class PdfService {

    private final PdfFileRepository pdfFileRepository;
    private final UserRepository userRepository;

    public void handlePdfUpload(MultipartFile file) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserDetails userDetails)) {
            log.warn("🚨 인증 객체가 UserDetails가 아님: {}", principal);
            throw new AuthException("인증된 사용자를 찾을 수 없습니다.");
        }

        String email = userDetails.getUsername();
        log.info("📨 JWT 토큰에서 추출한 이메일: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("❌ 이메일로 유저를 찾을 수 없음: {}", email);
                    return new NotFoundException("유저를 찾을 수 없습니다.");
                });

        log.info("✅ 유저 조회 성공: user_id = {}", user.getId());

        PdfFile pdfFile = PdfFile.builder()
                .fileName(file.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .build();

        PdfFile saved = pdfFileRepository.save(pdfFile);

        log.info("📦 PDF 저장 완료. pdfId = {}", saved.getPdfId());
    }
}
