package com.example.bookglebookgleserver.pdf.service;

import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.repository.PdfFileRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final PdfFileRepository pdfFileRepository;
    private final UserRepository userRepository;

    public void handlePdfUpload(MultipartFile file) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserDetails userDetails)) {
            System.out.println("🚨 principal이 UserDetails가 아님: " + principal);
            throw new RuntimeException("인증된 사용자를 찾을 수 없습니다.");
        }

        String email = userDetails.getUsername();
        System.out.println("📨 JWT 토큰에서 추출한 이메일: " + email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.out.println("❌ 이메일로 유저 못 찾음: " + email);
                    return new RuntimeException("유저를 찾을 수 없습니다.");
                });

        System.out.println("✅ 유저 조회 성공: user_id = " + user.getId());

        PdfFile pdfFile = PdfFile.builder()
                .fileName(file.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .build();

        PdfFile saved = pdfFileRepository.save(pdfFile);

        System.out.println("📦 PDF 저장 완료. ID: " + saved.getPdfId());
    }

}
