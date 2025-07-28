package com.example.bookglebookgleserver.group.service;

import com.bgbg.ai.grpc.ProcessPdfResponse;
import com.example.bookglebookgleserver.global.util.AuthUtil;
import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.ocr.grpc.GrpcOcrClient;
import com.example.bookglebookgleserver.ocr.service.OcrService;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.repository.PdfFileRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final PdfFileRepository pdfRepository;
    private final UserRepository userRepository;
    private final GrpcOcrClient grpcOcrClient;
    private final OcrService ocrService;

    @Override
    @Transactional
    public void createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, String token) {
        // ✅ 1. 사용자 인증 유틸 사용
        String email = AuthUtil.getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 2. PDF 저장
        PdfFile pdf = PdfFile.builder()
                .fileName(pdfFile.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .createdAt(LocalDateTime.now())
                .build();

        PdfFile savedPdf = pdfRepository.save(pdf);

        // 3. OCR 필요 시 gRPC 요청 및 결과 저장
        if (dto.isImageBased()) {
            ProcessPdfResponse response = grpcOcrClient.sendPdf(savedPdf.getPdfId(), pdfFile);
            ocrService.saveOcrResults(savedPdf, response);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        Group group = Group.builder()
                .roomTitle(dto.getRoomTitle())
                .description(dto.getDescription())
                .category(Group.Category.valueOf(dto.getCategory().toUpperCase()))
                .minRequiredRating(dto.getMinRequiredRating())
                .schedule(LocalDateTime.parse(dto.getSchedule(), formatter)) // ← 여기!
                .groupMaxNum(dto.getGroupMaxNum())
                .readingMode(Group.ReadingMode.valueOf(dto.getReadingMode().toUpperCase()))
                .hostUser(user)
                .pdfFile(savedPdf)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        groupRepository.save(group);
    }

    @Override
    public void createGroupWithoutOcr(GroupCreateRequestDto dto, MultipartFile pdfFile, String token) {
        dto.setImageBased(false); // 강제 비활성화
        createGroup(dto, pdfFile, token);
    }
}
