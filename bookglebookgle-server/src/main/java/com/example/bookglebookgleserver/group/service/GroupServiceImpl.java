package com.example.bookglebookgleserver.group.service;

import com.bgbg.ai.grpc.ProcessPdfResponse;
import com.bgbg.ai.grpc.TextBlock;
import com.example.bookglebookgleserver.global.exception.BadRequestException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.global.util.AuthUtil;
import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.dto.GroupListResponseDto;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
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

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PdfFileRepository pdfRepository;
    private final UserRepository userRepository;
    private final GrpcOcrClient grpcOcrClient;
    private final OcrService ocrService;

    @Override
    @Transactional
    public void createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, String token) {
        // 1. 사용자 인증
        String email = AuthUtil.getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        // 2. PDF 저장
        // 현재 실행 경로 + /uploads
//      String uploadDir = System.getProperty("user.dir") + "/uploads/";

        String uploadDir = "/home/ubuntu/pdf-uploads/";
        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs(); // 자동 생성
        }

        String storedFileName = UUID.randomUUID() + "_" + pdfFile.getOriginalFilename();
        String filePath = uploadDir + storedFileName;

        try {
            pdfFile.transferTo(new File(filePath));
        } catch (IOException e) {
            log.error("❌ PDF 파일 저장 실패", e);
            throw new BadRequestException("PDF 파일 저장 중 오류가 발생했습니다.");
        }

        PdfFile pdf = PdfFile.builder()
                .fileName(pdfFile.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .createdAt(LocalDateTime.now())
                .filePath(filePath)
                .build();

        PdfFile savedPdf = pdfRepository.save(pdf);

        // 3. OCR 필요 시 요청
        if (dto.isImageBased()) {
            log.info("🟡 OCR 요청 시작 - PDF ID: {}, 파일명: {}", savedPdf.getPdfId(), pdfFile.getOriginalFilename());

            ProcessPdfResponse response;
            try {
                response = grpcOcrClient.sendPdf(savedPdf.getPdfId(), pdfFile);
            } catch (Exception e) {
                log.error("🔴 OCR 서버 통신 오류", e);
                throw new BadRequestException("OCR 서버와의 통신 중 오류가 발생했습니다.");
            }

            if (!response.getSuccess()) {
                log.error("🔴 OCR 실패 응답 수신: {}", response.getMessage());
                throw new BadRequestException("OCR 처리 실패: " + response.getMessage());
            }

            log.info("🟢 OCR 응답 수신 완료 - 블록 수: {}", response.getTextBlocksCount());
            if (response.getTextBlocksCount() > 0) {
                TextBlock block = response.getTextBlocks(0);
                log.info(" - 첫 번째 블럭 내용: [{}] (페이지: {})", block.getText(), block.getPageNumber());
            }

            ocrService.saveOcrResults(savedPdf, response);
        }

        Group group = Group.builder()
                .roomTitle(dto.getRoomTitle())
                .description(dto.getDescription())
                .category(Group.Category.valueOf(dto.getCategory().toUpperCase()))
                .minRequiredRating(dto.getMinRequiredRating())
                .schedule(dto.getSchedule())
                .groupMaxNum(dto.getGroupMaxNum())
                .readingMode(Group.ReadingMode.valueOf(dto.getReadingMode().toUpperCase()))
                .hostUser(user)
                .pdfFile(savedPdf)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        groupRepository.save(group);

        // 그룹 멤버로 생성자 추가
        GroupMember groupMember = GroupMember.builder()
                .group(group)
                .user(user)
                .isHost(true)
                .lastPageRead(0)
                .progressPercent(0f)
                .isFollowingHost(false) // 기본값. 필요 시 true로 설정
                .build();

        groupMemberRepository.save(groupMember);
        log.info("🟢 그룹 생성자 '{}'를 그룹 멤버로 자동 등록 완료", user.getEmail());
    }

    @Override
    public void createGroupWithoutOcr(GroupCreateRequestDto dto, MultipartFile pdfFile, String token) {
        dto.setImageBased(false); // 강제 비활성화
        createGroup(dto, pdfFile, token);
    }

    @Override
    public List<GroupListResponseDto> getGroupList() {
        log.info("📌 [GroupService] 그룹 목록 조회 시작");

        List<Group> groups = groupRepository.findAll();
        log.info("📌 [GroupService] 조회된 그룹 수: {}", groups.size());

        return groups.stream()
                .map(group -> {
                    try {
                        log.info("📌 그룹 ID: {}, 제목: {}", group.getId(), group.getRoomTitle());

                        int currentNum = groupMemberRepository.countByGroup(group);  // 💥 예외 가능성
                        log.info("📌 currentNum 조회 완료: {}", currentNum);

                        return GroupListResponseDto.builder()
                                .groupId(group.getId())
                                .roomTitle(group.getRoomTitle())
                                .description(group.getDescription())
                                .category(group.getCategory().name())
                                .groupMaxNum(group.getGroupMaxNum())
                                .currentNum(currentNum)
                                .minimumRating(group.getMinRequiredRating())
                                .build();
                    } catch (Exception e) {
                        log.error("❌ 그룹 ID {} 의 currentNum 조회 중 예외 발생", group.getId(), e);
                        throw new RuntimeException("그룹 정보 처리 중 오류 발생");
                    }
                })
                .collect(java.util.stream.Collectors.toList()); // ✅ Java 11 이하 대응
    }


}

