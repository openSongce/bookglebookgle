package com.example.bookglebookgleserver.pdf.service;

import com.example.bookglebookgleserver.global.exception.AuthException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.example.bookglebookgleserver.pdf.util.PdfUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j // 로깅을 위한 Lombok 어노테이션
public class PdfService {

    private final PdfFileRepository pdfFileRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PdfReadingProgressRepository pdfReadingProgressRepository;
    private final GroupMemberRepository groupMemberRepository;

    public void handlePdfUpload(MultipartFile file) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserDetails userDetails)) {
            log.warn(" 인증 객체가 UserDetails가 아님: {}", principal);
            throw new AuthException("인증된 사용자를 찾을 수 없습니다.");
        }

        String email = userDetails.getUsername();
        log.info("JWT 토큰에서 추출한 이메일: {}", email);


        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("이메일로 유저를 찾을 수 없음: {}", email);
                    return new NotFoundException("유저를 찾을 수 없습니다.");
                });

        log.info("유저 조회 성공: user_id = {}", user.getId());

        PdfFile pdfFile = PdfFile.builder()
                .fileName(file.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .build();

        PdfFile saved = pdfFileRepository.save(pdfFile);

        log.info("📦 PDF 저장 완료. pdfId = {}", saved.getPdfId());
    }


    @Transactional(readOnly = true)
    public PdfProgressResponse getProgress(User user, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("그룹이 존재하지 않습니다."));

        int totalPages = group.getTotalPages();  // group에 페이지 수 저장되어 있어야 함

//        // 최대 페이지 기준으로 progress 계산
//        int maxReadPage = pdfReadingProgressRepository.findByUserAndGroup(user, group)
//                .map(PdfReadingProgress::getMaxReadPage) // 새 컬럼
//                .orElse(0);
// 1) PRP 기준 기본값은 0

        
        int prpMax = pdfReadingProgressRepository.findByUserAndGroup(user, group)
                .map(PdfReadingProgress::getMaxReadPage)
                .orElse(0);

        // 2) GM 값도 받아와서 보조로 사용
        int gmMax = groupMemberRepository.findByGroupAndUser(group, user)
                .map(GroupMember::getMaxReadPage)
                .orElse(0);

        // 3) 두 테이블 중 더 큰 값 사용
        int maxReadPage = Math.max(prpMax, gmMax);

        // 4) 총 1페이지 문서면, 아직 0이라면 1로 보정 (정책)
        if (totalPages == 1 && maxReadPage == 0) {
            maxReadPage = 1;
        }

        // 5) 범위 보정
        if (totalPages > 0) {
            maxReadPage = Math.min(maxReadPage, totalPages);
        }





        double progressRate = (totalPages == 0) ? 0 : (maxReadPage * 100.0) / totalPages;

        return new PdfProgressResponse(groupId, maxReadPage, totalPages, Math.round(progressRate * 100.0) / 100.0);
    }

    @Transactional
    public void updateOrInsertProgress(Long userId, Long groupId, int page) {
        User user = userRepository.findById(userId).orElseThrow();
        Group group = groupRepository.findById(groupId).orElseThrow();

        // 있으면 한 방에 MAX 갱신
        int updated = pdfReadingProgressRepository.bumpMaxReadPage(user, group, page);
        if (updated == 0) {
            // 없으면 생성
            PdfReadingProgress progress = PdfReadingProgress.builder()
                    .user(user)
                    .group(group)
                    .maxReadPage(page)
                    .build();
            pdfReadingProgressRepository.save(progress);
        }
    }

    @Transactional
    public int bumpMaxRead(long userId, long groupId, int page) {
        return pdfReadingProgressRepository.bumpMaxReadPage(userId, groupId, page);
    }

    @Transactional
    public int setProgressPercent(long userId, long groupId, int percent) {
        return pdfReadingProgressRepository.updateProgressPercent(userId, groupId, percent);
    }


}
