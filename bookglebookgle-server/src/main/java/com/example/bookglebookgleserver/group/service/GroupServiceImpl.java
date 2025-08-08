package com.example.bookglebookgleserver.group.service;

import com.bgbg.ai.grpc.AIServiceProto.ProcessPdfResponse;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.chat.entity.ChatRoomMember;
import com.example.bookglebookgleserver.chat.repository.ChatRoomMemberRepository;
import com.example.bookglebookgleserver.chat.repository.ChatRoomRepository;
import com.example.bookglebookgleserver.fcm.service.GroupNotificationScheduler;
import com.example.bookglebookgleserver.fcm.util.KoreanScheduleParser;
import com.example.bookglebookgleserver.global.exception.BadRequestException;
import com.example.bookglebookgleserver.global.exception.ForbiddenException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.group.dto.*;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.ocr.dto.OcrTextBlockDto;
import com.example.bookglebookgleserver.ocr.grpc.GrpcOcrClient;
import com.example.bookglebookgleserver.ocr.service.OcrService;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.repository.PdfFileRepository;
import com.example.bookglebookgleserver.pdf.util.PdfUtils;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;
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
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final GroupNotificationScheduler groupNotificationScheduler;

    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Seoul");

    @Override
    @Transactional
    public GroupCreateResponseDto createGroup(GroupCreateRequestDto dto, MultipartFile pdfFile, User user) {
        String uploadDir = "/home/ubuntu/pdf-uploads/";
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

        PdfFile pdf = PdfFile.builder()
                .fileName(pdfFile.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .createdAt(LocalDateTime.now())
                .filePath(filePath)
                .build();
        pdfFileRepository.save(pdf);

        int pageCount = PdfUtils.getPageCount(filePath);
        pdf.setPageCnt(pageCount);
        pdfFileRepository.save(pdf);
        log.info("📄 PDF 페이지 수 추출 완료: {} 페이지", pageCount);

        // ✅ 스케줄 자연어 → CRON 변환 (CRON이면 그대로)
        String scheduleInput = dto.getSchedule();
        String cron = null;
        if (scheduleInput != null && !scheduleInput.isBlank()) {
            try {
                try { new CronTrigger(scheduleInput, TZ); cron = scheduleInput; }
                catch (Exception ignore) { cron = KoreanScheduleParser.toCron(scheduleInput); }
            } catch (Exception e) {
                log.warn("⚠️ 그룹 스케줄 파싱 실패: 입력='{}', reason={}. 스케줄 없이 생성합니다.", scheduleInput, e.getMessage());
            }
        }

        Group group = Group.builder()
                .roomTitle(dto.getRoomTitle())
                .description(dto.getDescription())
                .category(Group.Category.valueOf(dto.getCategory().toUpperCase()))
                .minRequiredRating(dto.getMinRequiredRating())
                .schedule(cron) // 변환된 CRON 저장(없으면 null)
                .groupMaxNum(dto.getGroupMaxNum())
                .readingMode(Group.ReadingMode.valueOf(dto.getReadingMode().toUpperCase()))
                .hostUser(user)
                .pdfFile(pdf)
                .totalPages(pageCount)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        groupRepository.save(group);

        // 그룹 생성 직후 스케줄 등록
        if (cron != null) {
            try {
                groupNotificationScheduler.register(group.getId(), cron);
            } catch (Exception e) {
                log.warn("⚠️ 스케줄 등록 실패(그룹 생성 직후): groupId={}, cron='{}', reason={}", group.getId(), cron, e.getMessage());
            }
        }

        pdf.setGroup(group);

        List<OcrTextBlockDto> ocrResultList = null;

        if (dto.isImageBased()) {
            ProcessPdfResponse response = grpcOcrClient.sendPdf(pdf.getPdfId(), pdfFile, group.getId());
            if (!response.getSuccess()) {
                throw new BadRequestException("OCR 실패: " + response.getMessage());
            }
            ocrService.saveOcrResults(pdf, response);

            ocrResultList = response.getTextBlocksList().stream()
                    .map(block -> OcrTextBlockDto.builder()
                            .pageNumber(block.getPageNumber())
                            .text(block.getText())
                            .rectX(block.getX0())
                            .rectY(block.getY0())
                            .rectW((block.getX1() - block.getX0()))
                            .rectH((block.getY1() - block.getY0()))
                            .build())
                    .collect(Collectors.toList());
        } else {
            grpcOcrClient.sendPdfNoOcr(pdf.getPdfId(), pdfFile, group.getId());
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .group(group)
                .category(group.getCategory().name())
                .groupTitle(group.getRoomTitle())
                .imageUrl(null)
                .lastMessage(null)
                .lastMessageTime(null)
                .memberCount(1)
                .build();
        chatRoomRepository.save(chatRoom);
        log.info("[GroupService] 그룹 생성 및 채팅방 생성 완료 - groupId={}, chatRoom memberCount=1", group.getId());

        ChatRoomMember chatRoomMember = ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .user(user)
                .build();
        chatRoomMemberRepository.save(chatRoomMember);

        GroupMember groupMember = GroupMember.builder()
                .group(group)
                .user(user)
                .isHost(true)
                .lastPageRead(0)
                .progressPercent(0f)
                .isFollowingHost(false)
                .build();
        groupMemberRepository.save(groupMember);

        return GroupCreateResponseDto.builder()
                .groupId(group.getId())
                .pdfId(pdf.getPdfId())
                .ocrResultlist(ocrResultList != null ? ocrResultList : List.of())
                .build();
    }

    @Override
    public List<GroupListResponseDto> getNotJoinedGroupList(Long userId) {
        log.info("📌 [GroupService] (미가입자용) 그룹 목록 조회 시작");

        List<Group> groups = groupRepository.findAll();
        log.info("📌 [GroupService] 전체 그룹 수: {}", groups.size());

        List<Long> joinedGroupIds = groupMemberRepository.findGroupIdsByUserId(userId);
        log.info("📌 [GroupService] 사용자가 가입한 그룹 수: {}", joinedGroupIds.size());

        return groups.stream()
                .filter(group -> !joinedGroupIds.contains(group.getId()))
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
    public GroupDetailResponse getGroupDetail(Long groupId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("해당 모임이 존재하지 않습니다."));

        int memberCount = groupMemberRepository.countByGroup(group);
        boolean isHost = group.getHostUser().getId().equals(user.getId());

        return new GroupDetailResponse(
                group.getRoomTitle(),
                group.getCategory().name(),
                group.getSchedule(),
                memberCount,
                group.getGroupMaxNum(),
                group.getDescription(),
                null,
                isHost,
                group.getMinRequiredRating()
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
                        group.getGroupMaxNum(),
                        group.getHostUser().getId().equals(userId)
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void joinGroup(Long groupId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("해당 그룹이 존재하지 않습니다."));

        if (groupMemberRepository.existsByGroupAndUser(group, user)) {
            throw new BadRequestException("이미 참가한 그룹입니다.");
        }

        int currentNum = groupMemberRepository.countByGroup(group);
        if (currentNum >= group.getGroupMaxNum()) {
            throw new BadRequestException("그룹 정원이 초과되었습니다.");
        }

        if (user.getAvgRating() == null || user.getAvgRating() < group.getMinRequiredRating()) {
            throw new BadRequestException("평점이 낮아 그룹에 참가할 수 없습니다.");
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .isHost(false)
                .lastPageRead(0)
                .progressPercent(0f)
                .isFollowingHost(false)
                .build();
        groupMemberRepository.save(member);

        ChatRoom chatRoom = chatRoomRepository.findByGroupId(groupId)
                .orElseThrow(() -> new NotFoundException("채팅방 없음"));

        ChatRoomMember chatMember = ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .user(user)
                .build();
        chatRoomMemberRepository.save(chatMember);

        chatRoom.setMemberCount(chatRoom.getMemberCount() + 1);
        chatRoomRepository.save(chatRoom);

        log.info("[GroupService] userId={} 그룹 {} 참가 및 채팅방 멤버 추가 완료, memberCount={}",
                user.getId(), groupId, chatRoom.getMemberCount());
    }

    @Override
    @Transactional
    public GroupDetailResponse updateGroup(Long groupId, GroupUpdateRequestDto dto, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("해당 모임이 존재하지 않습니다."));

        if (!group.getHostUser().getId().equals(user.getId())) {
            throw new ForbiddenException("그룹 수정 권한이 없습니다.");
        }

        if (dto.getRoomTitle() != null) group.setRoomTitle(dto.getRoomTitle());
        if (dto.getDescription() != null) group.setDescription(dto.getDescription());
        if (dto.getCategory() != null) group.setCategory(Group.Category.valueOf(dto.getCategory().toUpperCase()));
        if (dto.getGroupMaxNum() > 0) group.setGroupMaxNum(dto.getGroupMaxNum());
        if (dto.getMinRequiredRating() > 0) group.setMinRequiredRating(dto.getMinRequiredRating());
        if (dto.getReadingMode() != null) group.setReadingMode(Group.ReadingMode.valueOf(dto.getReadingMode().toUpperCase()));

        // ✅ 스케줄 업데이트 처리
        if (dto.getSchedule() != null) {
            String input = dto.getSchedule();
            if (input.isBlank()) {
                // 스케줄 해제
                group.setSchedule(null);
                groupNotificationScheduler.unregister(groupId);
                log.info("🗑️ 그룹 스케줄 해제: groupId={}", groupId);
            } else {
                try {
                    String cron;
                    try { new CronTrigger(input, TZ); cron = input; }
                    catch (Exception ignore) { cron = KoreanScheduleParser.toCron(input); }
                    group.setSchedule(cron);
                    groupNotificationScheduler.register(groupId, cron);
                } catch (Exception e) {
                    throw new BadRequestException("스케줄 형식이 올바르지 않습니다: " + e.getMessage());
                }
            }
        }

        int memberCount = groupMemberRepository.countByGroup(group);
        boolean isHost = group.getHostUser().getId().equals(user.getId());

        return new GroupDetailResponse(
                group.getRoomTitle(),
                group.getCategory().name(),
                group.getSchedule(),
                memberCount,
                group.getGroupMaxNum(),
                group.getDescription(),
                null,
                isHost,
                group.getMinRequiredRating()
        );
    }

    @Override
    @Transactional
    public void deleteGroup(Long groupId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("그룹이 존재하지 않습니다."));
        if (!group.getHostUser().getId().equals(user.getId())) {
            throw new ForbiddenException("그룹 삭제 권한이 없습니다.");
        }
        // ✅ 스케줄 해제 후 삭제
        groupNotificationScheduler.unregister(groupId);
        groupRepository.delete(group);
        log.info("🗑️ 그룹 삭제 완료 및 스케줄 해제: groupId={}", groupId);
    }

    @Override
    public List<GroupListResponseDto> searchGroups(String roomTitle, String category) {
        Group.Category categoryEnum = null;
        if (category != null && !category.isBlank()) {
            categoryEnum = Group.Category.valueOf(category.toUpperCase());
        }
        List<Group> groups = groupRepository.searchGroups(roomTitle, categoryEnum);

        return groups.stream()
                .map(GroupListResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void leaveGroup(Long groupId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("해당 그룹이 존재하지 않습니다."));

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new BadRequestException("해당 그룹에 가입되어 있지 않습니다."));

        if (group.getHostUser().getId().equals(user.getId())) {
            throw new ForbiddenException("그룹장은 직접 탈퇴할 수 없습니다. 그룹장 권한 위임 후 탈퇴하세요.");
        }

        groupMemberRepository.delete(member);

        ChatRoom chatRoom = chatRoomRepository.findByGroupId(groupId)
                .orElseThrow(() -> new NotFoundException("채팅방 없음"));

        ChatRoomMember chatRoomMember = chatRoomMemberRepository.findByChatRoomAndUser(chatRoom, user)
                .orElse(null);

        if (chatRoomMember != null) {
            chatRoomMemberRepository.delete(chatRoomMember);
            chatRoom.setMemberCount(Math.max(0, chatRoom.getMemberCount() - 1));
            chatRoomRepository.save(chatRoom);

            log.info("[GroupService] userId={} 그룹 {} 탈퇴 및 채팅방 멤버 삭제 완료, memberCount={}",
                    user.getId(), groupId, chatRoom.getMemberCount());
        }
    }

    @Override
    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroup_IdAndUser_Id(groupId, userId);
    }

    @Override
    public boolean isLeader(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("해당 그룹이 존재하지 않습니다."));
        return group.getHostUser().getId().equals(userId);
    }

    @Override
    public int getLastPageRead(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("그룹을 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new NotFoundException("해당 그룹 멤버를 찾을 수 없습니다."));

        return member.getLastPageRead();
    }
}
