package com.example.bookglebookgleserver.group.service;

import com.bgbg.ai.grpc.ProcessPdfResponse;
import com.example.bookglebookgleserver.global.exception.BadRequestException;
import com.example.bookglebookgleserver.global.exception.ForbiddenException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.dto.GroupDetailResponse;
import com.example.bookglebookgleserver.group.dto.GroupListResponseDto;
import com.example.bookglebookgleserver.group.dto.MyGroupSummaryDto;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.ocr.grpc.GrpcOcrClient;
import com.example.bookglebookgleserver.ocr.service.OcrService;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.repository.PdfFileRepository;
import com.example.bookglebookgleserver.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PdfFileRepository pdfFileRepository;
    private final GrpcOcrClient grpcOcrClient;
    private final OcrService ocrService;

    @Override
    @Transactional
    public void createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, User user) {
        String uploadDir = "/home/ubuntu/pdf-uploads/";
//        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }

        String storedFileName = UUID.randomUUID() + "_" + pdfFile.getOriginalFilename();
        String filePath = uploadDir + storedFileName;

        try {
            pdfFile.transferTo(new File(filePath));
        } catch (IOException e) {
            throw new BadRequestException("PDF 파일 저장 중 오류가 발생했습니다.");
        }

        // 📌 PdfFile 먼저 생성 (group은 null)
        PdfFile pdf = PdfFile.builder()
                .fileName(pdfFile.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .createdAt(LocalDateTime.now())
                .filePath(filePath)
                .build();
        pdfFileRepository.save(pdf);  // 1차 저장

        // 📌 Group 생성 시 PdfFile 연결
        Group group = Group.builder()
                .roomTitle(dto.getRoomTitle())
                .description(dto.getDescription())
                .category(Group.Category.valueOf(dto.getCategory().toUpperCase()))
                .minRequiredRating(dto.getMinRequiredRating())
                .schedule(dto.getSchedule())
                .groupMaxNum(dto.getGroupMaxNum())
                .readingMode(Group.ReadingMode.valueOf(dto.getReadingMode().toUpperCase()))
                .hostUser(user)
                .pdfFile(pdf)  // 연결
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        groupRepository.save(group);

        // 📌 역방향 연결 (중요)
        pdf.setGroup(group);
        // pdfFileRepository.save(pdf); // ❌ 생략해도 무방 (영속성 컨텍스트 안에서 관리됨)

        if (dto.isImageBased()) {
            ProcessPdfResponse response = grpcOcrClient.sendPdf(pdf.getPdfId(), pdfFile);
            if (!response.getSuccess()) {
                throw new BadRequestException("OCR 실패: " + response.getMessage());
            }
            ocrService.saveOcrResults(pdf, response);
        }

        GroupMember groupMember = GroupMember.builder()
                .group(group)
                .user(user)
                .isHost(true)
                .lastPageRead(0)
                .progressPercent(0f)
                .isFollowingHost(false)
                .build();
        groupMemberRepository.save(groupMember);
    }

    @Override
    public void createGroupWithoutOcr(GroupCreateRequestDto dto, MultipartFile pdfFile, User user) {
        dto.setImageBased(false);
        createGroup(dto, pdfFile, user);
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

                        int currentNum = groupMemberRepository.countByGroup(group);
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
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public GroupDetailResponse getGroupDetail(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("해당 모임이 존재하지 않습니다."));

        int memberCount = groupMemberRepository.countByGroup(group);

        return new GroupDetailResponse(
                group.getRoomTitle(),
                group.getCategory().name(),
                group.getSchedule(),
                memberCount,
                group.getGroupMaxNum(),
                group.getDescription(),
                null
        );
    }

    @Override
    public ResponseEntity<Resource> getPdfFileResponse(Long groupId, User user) {
        if (!groupMemberRepository.existsByGroup_IdAndUser_Id(groupId, user.getId())) {
            throw new ForbiddenException("해당 그룹에 속해 있지 않습니다.");
        }

        PdfFile pdfFile = pdfFileRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new NotFoundException("PDF 파일이 없습니다."));

        File file = new File(pdfFile.getFilePath());
        if (!file.exists()) {
            throw new NotFoundException("서버에 PDF 파일이 존재하지 않습니다.");
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + pdfFile.getFileName() + "\"")
                .body(resource);
    }

    @Override
    public List<MyGroupSummaryDto> getMyGroupList(Long userId) {
        List<GroupMember> memberships = groupMemberRepository.findByUser_Id(userId);

        return memberships.stream()
                .map(GroupMember::getGroup)
                .distinct()
                .map(group -> new MyGroupSummaryDto(
                        group.getId(),
                        group.getRoomTitle(),
                        group.getDescription(),
                        null, // 이미지 URL은 아직 없음
                        group.getCategory().name(),
                        group.getGroupMembers().size(),
                        group.getGroupMaxNum()
                ))
                .collect(Collectors.toList());
    }
}
