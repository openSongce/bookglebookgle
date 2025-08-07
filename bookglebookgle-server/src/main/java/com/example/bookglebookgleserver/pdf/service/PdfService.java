package com.example.bookglebookgleserver.pdf.service;

import com.example.bookglebookgleserver.global.exception.AuthException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.pdf.dto.PdfProgressResponse;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.entity.PdfReadingProgress;
import com.example.bookglebookgleserver.pdf.repository.PdfFileRepository;
import com.example.bookglebookgleserver.pdf.repository.PdfReadingProgressRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.bookglebookgleserver.pdf.util.PdfUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j // ✅ 로깅을 위한 Lombok 어노테이션
public class PdfService {

    private final PdfFileRepository pdfFileRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PdfReadingProgressRepository pdfReadingProgressRepository;

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


    public PdfProgressResponse getProgress(User user, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("그룹이 존재하지 않습니다."));

        int totalPages = group.getTotalPages();  // group에 페이지 수 저장되어 있어야 함

        int lastReadPage = pdfReadingProgressRepository.findByUserAndGroup(user, group)
                .map(PdfReadingProgress::getLastReadPage)
                .orElse(0);

        double progressRate = (totalPages == 0) ? 0 : (lastReadPage * 100.0) / totalPages;

        return new PdfProgressResponse(groupId, lastReadPage, totalPages, Math.round(progressRate * 100.0) / 100.0);
    }

    public void updateProgress(User user, Group group, int page) {
        Optional<PdfReadingProgress> optional = pdfReadingProgressRepository.findByUserAndGroup(user, group);

        if (optional.isPresent()) {
            pdfReadingProgressRepository.updateLastReadPage(user.getId(), group.getId(), page);
        } else {
            PdfReadingProgress progress = PdfReadingProgress.builder()
                    .user(user)
                    .group(group)
                    .lastReadPage(page)
                    .updatedAt(LocalDateTime.now())
                    .build();
            pdfReadingProgressRepository.save(progress);
        }
    }



}
