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
import com.example.bookglebookgleserver.group.repository.GroupMemberRatingRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
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
    private final GroupMemberRatingRepository groupMemberRatingRepository;

    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Seoul");

    @Override
    @Transactional
    public GroupCreateResponseDto createGroup(GroupCreateRequestDto dto, MultipartFile pdfUpload, User user) {
        String uploadDir = "/home/ubuntu/pdf-uploads/";
        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) uploadDirFile.mkdirs();

        String storedFileName = UUID.randomUUID() + "_" + pdfUpload.getOriginalFilename();
        String filePath = uploadDir + storedFileName;

        try {
            pdfUpload.transferTo(new File(filePath));
        } catch (IOException e) {
            throw new BadRequestException("PDF 파일 저장 중 오류가 발생했습니다.");
        }

        PdfFile pdf = PdfFile.builder()
                .fileName(pdfUpload.getOriginalFilename())
                .pageCnt(0)
                .uploadUser(user)
                .createdAt(LocalDateTime.now())
                .filePath(filePath)
                .imageBased(dto.isImageBased())
                .hasOcr(false)
                .build();
        pdfFileRepository.save(pdf);

        int pageCount = PdfUtils.getPageCount(filePath);
        pdf.setPageCnt(pageCount);
        pdfFileRepository.save(pdf);
        log.info("📄 PDF 페이지 수 추출 완료: {} 페이지", pageCount);

        // 스케줄 CRON 변환
        String cron = null;
        if (dto.getSchedule() != null && !dto.getSchedule().isBlank()) {
            try {
                try { new CronTrigger(dto.getSchedule(), TZ); cron = dto.getSchedule(); }
                catch (Exception ignore) { cron = KoreanScheduleParser.toCron(dto.getSchedule()); }
            } catch (Exception e) {
                log.warn("⚠️ 그룹 스케줄 파싱 실패: 입력='{}', reason={}. 스케줄 없이 생성.", dto.getSchedule(), e.getMessage());
            }
        }

        Group group = Group.builder()
                .roomTitle(dto.getRoomTitle())
                .description(dto.getDescription())
                .category(Group.Category.valueOf(dto.getCategory().toUpperCase()))
                .minRequiredRating(dto.getMinRequiredRating())
                .schedule(cron)
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

        if (cron != null) {
            try {
                groupNotificationScheduler.register(group.getId(), cron);
            } catch (Exception e) {
                log.warn("⚠️ 스케줄 등록 실패: groupId={}, cron='{}', reason={}", group.getId(), cron, e.getMessage());
            }
        }

        pdf.setGroup(group);

        List<OcrTextBlockDto> ocrResultList = List.of();

        // ✅ OCR 처리 분기
        if (dto.isImageBased()) {
            ProcessPdfResponse response = grpcOcrClient.sendPdf(pdf.getPdfId(), pdfUpload, group.getId());
            if (!response.getSuccess()) {
                log.warn("❌ OCR 실패: {}", response.getMessage());
                throw new BadRequestException("OCR 실패: " + response.getMessage());
            }
            ocrService.saveOcrResults(pdf, response);
            pdf.setHasOcr(true);
            pdfFileRepository.save(pdf);

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
            grpcOcrClient.sendPdfNoOcr(pdf.getPdfId(), pdfUpload, group.getId());
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

        ChatRoomMember chatRoomMember = ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .user(user)
                .build();
        chatRoomMemberRepository.save(chatRoomMember);

        GroupMember groupMember = GroupMember.builder()
                .group(group)
                .user(user)
                .isHost(true)
                .maxReadPage(0)
                .progressPercent(0f)
                .isFollowingHost(false)
                .build();
        groupMemberRepository.save(groupMember);

        return GroupCreateResponseDto.builder()
                .groupId(group.getId())
                .pdfId(pdf.getPdfId())
                .ocrResultlist(ocrResultList)
                .build();
    }

    @Override
    public List<GroupListResponseDto> getNotJoinedGroupList(Long userId) {
        List<Group> groups = groupRepository.findAll();
        List<Long> joinedGroupIds = groupMemberRepository.findGroupIdsByUserId(userId);

        return groups.stream()
                .filter(group -> !joinedGroupIds.contains(group.getId()))
                .map(group -> {
                    int currentNum = groupMemberRepository.countByGroup(group);
                    return GroupListResponseDto.builder()
                            .groupId(group.getId())
                            .roomTitle(group.getRoomTitle())
                            .description(group.getDescription())
                            .category(group.getCategory().name())
                            .groupMaxNum(group.getGroupMaxNum())
                            .currentNum(currentNum)
                            .minimumRating(group.getMinRequiredRating())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetail(Long groupId, User requester) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("해당 모임이 존재하지 않습니다."));

        int pageCount = resolvePageCount(group);
        boolean requesterIsHost = group.getHostUser().getId().equals(requester.getId());

        // 기본 멤버 정보
        List<GroupMemberDetailDto> base = groupMemberRepository.findMemberDetailsByGroupId(groupId);
        // 2) (from→to) 벌크 조회 후 Map<Long, List<Long>>로 변환
        List<Object[]> pairs = groupMemberRatingRepository.findAllFromToPairsByGroupId(groupId);
        Map<Long, List<Long>> fromToMap = pairs.stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],                                   // fromUserId
                        Collectors.mapping(row -> (Long) row[1],                // toUserId
                                Collectors.collectingAndThen(Collectors.toList(), list -> {
                                    // 중복 제거 + 불변 리스트
                                    return List.copyOf(new LinkedHashSet<>(list));
                                }))
                ));

        long otherMembersCountIfAll = Math.max(0, base.size() - 1);

        // 3) DTO에 ratedUserIds / ratingSubmitted / progressPercent 주입
        List<GroupMemberDetailDto> members = base.stream().map(m -> {
            int progressPercent = calcProgressPercent(m.maxReadPage(), pageCount);

            List<Long> ratedUserIds = fromToMap.getOrDefault(m.userId(), Collections.emptyList());
            boolean ratingSubmitted = (otherMembersCountIfAll > 0) && (ratedUserIds.size() == otherMembersCountIfAll);

            return new GroupMemberDetailDto(
                    m.userId(),
                    m.userNickName(),
                    m.profileColor(),
                    m.maxReadPage(),
                    progressPercent,
                    m.isHost(),
                    m.profileImageUrl(),
                    ratingSubmitted,
                    ratedUserIds
            );
        }).toList();

        boolean allMemberCompleted = !members.isEmpty() &&
                members.stream().allMatch(mm -> mm.progressPercent() >= 100);

        String readableSchedule = cronToReadable(group.getSchedule());

        return new GroupDetailResponse(
                group.getRoomTitle(),
                group.getCategory().name(),
                readableSchedule,
                members.size(),
                group.getGroupMaxNum(),
                group.getDescription(),
                null,
                requesterIsHost,
                group.getMinRequiredRating(),
                pageCount,
                members,
                allMemberCompleted
        );
    }





    // (호환) PDF만 반환
    @Override
    public ResponseEntity<Resource> getPdfFileResponse(Long groupId, User user) {
        if (!groupMemberRepository.isMember(groupId, user.getId())) {
            throw new ForbiddenException("해당 그룹에 속해 있지 않습니다.");
        }

        PdfFile pdfFile = pdfFileRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new NotFoundException("PDF 파일이 없습니다."));

        File file = new File(pdfFile.getFilePath());
        if (!file.exists()) {
            throw new NotFoundException("서버에 PDF 파일이 존재하지 않습니다.");
        }


        // UTF-8 바이트를 직접 헤더에 넣기 (가장 호환성 좋음)
        String raw = pdfFile.getFileName();
        String cd = "inline; filename*=UTF-8''" + URLEncoder.encode(raw, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", cd)
                .header("X-OCR-Available", String.valueOf(pdfFile.isHasOcr()))
                .body(new FileSystemResource(file));

//        return ResponseEntity.ok()
//                .contentType(MediaType.APPLICATION_PDF)
//                .header("Content-Disposition", "inline; filename=\"" + pdfFile.getFileName() + "\"")
//                .header("X-OCR-Available", String.valueOf(pdfFile.isHasOcr()))
//                .body(new FileSystemResource(file));
    }

    // ✅ Accept 헤더 기반: PDF 혹은 ZIP

    // after
    @Override
    public ResponseEntity<StreamingResponseBody> getPdfResponse(Long groupId, User user, String accept) {
        if (!groupMemberRepository.isMember(groupId, user.getId())) {
            throw new ForbiddenException("해당 그룹에 속해 있지 않습니다.");
        }

        PdfFile pdfFile = pdfFileRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new NotFoundException("PDF 파일이 없습니다."));

        boolean wantZip = wantsZip(accept);
        boolean hasOcr = pdfFile.isHasOcr();

        if (wantZip && hasOcr) {
            return getPdfAndOcrZip(groupId, user); // 이미 StreamingResponseBody 반환
        }

        File file = new File(pdfFile.getFilePath());
        if (!file.exists()) throw new NotFoundException("서버에 PDF 파일이 존재하지 않습니다.");

        // UTF-8 바이트를 직접 헤더에 넣기 (가장 호환성 좋음)
        String raw = pdfFile.getFileName();
        String cd = "inline; filename*=UTF-8''" + URLEncoder.encode(raw, StandardCharsets.UTF_8);

        StreamingResponseBody body = outputStream -> {
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                in.transferTo(outputStream);
                outputStream.flush();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", cd)
                .header("X-OCR-Available", String.valueOf(hasOcr))
                .body(body);

//        return ResponseEntity.ok()
//                .contentType(MediaType.APPLICATION_PDF)
//                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
//                .header("X-OCR-Available", String.valueOf(hasOcr))
//                .body(body);
    }


    @Override
    public ResponseEntity<StreamingResponseBody> getPdfAndOcrZip(Long groupId, User user) {
        if (!groupMemberRepository.isMember(groupId, user.getId())) {
            throw new ForbiddenException("해당 그룹에 속해 있지 않습니다.");
        }

        PdfFile pdfFile = pdfFileRepository.findByGroup_Id(groupId)
                .orElseThrow(() -> new NotFoundException("PDF 파일이 없습니다."));
        File file = new File(pdfFile.getFilePath());
        if (!file.exists()) throw new NotFoundException("서버에 PDF 파일이 존재하지 않습니다.");
        if (!pdfFile.isHasOcr()) throw new BadRequestException("해당 PDF는 OCR 결과가 없습니다.");

        // OCR 직렬화
        var blocks = ocrService.getOcrBlocksByPdfId(pdfFile.getPdfId());
        String ocrJson;
        try {
            ocrJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(blocks);
        } catch (Exception e) {
            throw new BadRequestException("OCR 직렬화 실패: " + e.getMessage());
        }

        StreamingResponseBody body = outputStream -> {
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(outputStream)) {
                // 1) PDF
                zos.putNextEntry(new java.util.zip.ZipEntry("document.pdf"));
                try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();

                // 2) OCR JSON
                zos.putNextEntry(new java.util.zip.ZipEntry("ocr.json"));
                zos.write(ocrJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.finish();
            }
        };

        // UTF-8 바이트를 직접 헤더에 넣기
        String zipName = "group-" + groupId + "-pdf-with-ocr.zip";
        String cd = "attachment; filename*=UTF-8''" + URLEncoder.encode(zipName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header("Content-Disposition", cd)
                .header("X-OCR-Available", "true")
                .header("X-Accel-Buffering", "no")
                .body(body);
//        return ResponseEntity.ok()
//                .contentType(MediaType.parseMediaType("application/zip"))
//                .header("Content-Disposition", "attachment; filename=\"" + zipName + "\"")
//                .header("X-OCR-Available", "true")
//                // Nginx 사용 시 버퍼링 방지(선택): .header("X-Accel-Buffering","no")
//                .body(body);
    }

    private static boolean wantsZip(String accept) {
        if (accept == null || accept.isBlank()) return false;
        try {
            for (var mt : MediaType.parseMediaTypes(accept)) {
                if (mt.isCompatibleWith(MediaType.valueOf("application/zip"))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String safeFilename(String name) {
        if (name == null) return "document";
        return name.replaceAll("[\\r\\n\"]", "_");
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
                .maxReadPage(0)
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

        // 그룹 정보 업데이트
        if (dto.getRoomTitle() != null) group.setRoomTitle(dto.getRoomTitle());
        if (dto.getDescription() != null) group.setDescription(dto.getDescription());
        if (dto.getCategory() != null) group.setCategory(Group.Category.valueOf(dto.getCategory().toUpperCase()));
        if (dto.getGroupMaxNum() > 0) group.setGroupMaxNum(dto.getGroupMaxNum());
        if (dto.getMinRequiredRating() > 0) group.setMinRequiredRating(dto.getMinRequiredRating());
        if (dto.getReadingMode() != null) group.setReadingMode(Group.ReadingMode.valueOf(dto.getReadingMode().toUpperCase()));

        // 스케줄 업데이트
        if (dto.getSchedule() != null) {
            String input = dto.getSchedule();
            if (input.isBlank()) {
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

        groupRepository.save(group);

        // === 응답 생성 ===
        boolean isHost = group.getHostUser().getId().equals(user.getId());
        int pageCount = resolvePageCount(group);

        // 1) 기본 멤버 정보
        List<GroupMemberDetailDto> base = groupMemberRepository.findMemberDetailsByGroupId(groupId);

        // 2) (from→to) 벌크 조회 후 Map<Long, List<Long>>로 변환
        List<Object[]> pairs = groupMemberRatingRepository.findAllFromToPairsByGroupId(groupId);
        Map<Long, List<Long>> fromToMap = pairs.stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],                                   // fromUserId
                        Collectors.mapping(row -> (Long) row[1],                // toUserId
                                Collectors.collectingAndThen(Collectors.toList(), list -> {
                                    // 중복 제거 + 불변 리스트
                                    return List.copyOf(new LinkedHashSet<>(list));
                                }))
                ));

        long otherMembersCountIfAll = Math.max(0, base.size() - 1);

        // 3) DTO에 ratedUserIds / ratingSubmitted / progressPercent 주입
        List<GroupMemberDetailDto> members = base.stream().map(m -> {
            int progressPercent = calcProgressPercent(m.maxReadPage(), pageCount);

            List<Long> ratedUserIds = fromToMap.getOrDefault(m.userId(), Collections.emptyList());
            boolean ratingSubmitted = (otherMembersCountIfAll > 0) && (ratedUserIds.size() == otherMembersCountIfAll);

            return new GroupMemberDetailDto(
                    m.userId(),
                    m.userNickName(),
                    m.profileColor(),
                    m.maxReadPage(),
                    progressPercent,
                    m.isHost(),
                    m.profileImageUrl(),
                    ratingSubmitted,
                    ratedUserIds
            );
        }).toList();

        boolean allReadCompleted = !members.isEmpty()
                && members.stream().allMatch(m -> m.progressPercent() >= 100);

        return new GroupDetailResponse(
                group.getRoomTitle(),
                group.getCategory().name(),
                group.getSchedule(),
                members.size(),
                group.getGroupMaxNum(),
                group.getDescription(),
                null,
                isHost,
                group.getMinRequiredRating(),
                pageCount,
                members,
                allReadCompleted
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
        return groups.stream().map(GroupListResponseDto::fromEntity).collect(Collectors.toList());
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

        ChatRoomMember chatRoomMember = chatRoomMemberRepository.findByChatRoomAndUser(chatRoom, user).orElse(null);

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
        return groupMemberRepository.isMember(groupId, userId);
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
        return member.getMaxReadPage();
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
                        null,
                        group.getCategory().name(),
                        group.getGroupMembers().size(),
                        group.getGroupMaxNum(),
                        group.getHostUser().getId().equals(userId)
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    private int resolvePageCount(Group group) {
        if (group.getPdfFile() != null) return group.getPdfFile().getPageCnt();
        // totalPages가 int면 그대로, Integer면 null 가드
        try { return group.getTotalPages(); } catch (NullPointerException e) { return 0; }
    }

    private static final Map<String, String> DAY_MAP = Map.of(
            "MON", "월요일",
            "TUE", "화요일",
            "WED", "수요일",
            "THU", "목요일",
            "FRI", "금요일",
            "SAT", "토요일",
            "SUN", "일요일"
    );

    private String cronToReadable(String cron) {
        String[] parts = cron.split("\\s+");
        if (parts.length < 6) return cron;

        int minute = Integer.parseInt(parts[1]); // 분
        int hour = Integer.parseInt(parts[2]);   // 시

        String ampm = (hour < 12) ? "오전" : "오후";
        int displayHour = (hour == 0) ? 12 : (hour <= 12 ? hour : hour - 12);
        String dayKorean = DAY_MAP.getOrDefault(parts[5], parts[5]);

        if (minute == 0) {
            return String.format("매주 %s %s %d시", dayKorean, ampm, displayHour);
        } else {
            return String.format("매주 %s %s %d시 %d분", dayKorean, ampm, displayHour, minute);
        }
    }

    private int calcProgressPercent(int maxReadPage, int pageCount) {
        if (pageCount <= 0) return 0;

        // 단일 페이지 문서: 0번 페이지만 존재
        if (pageCount == 1) {
            // maxReadPage가 0 이상이면 읽은 것으로 보고 100%
            return (maxReadPage >= 0) ? 100 : 0;
        }

        // 다중 페이지(0..pageCount-1)에서 0-based로 계산
        int clamped = Math.min(Math.max(0, maxReadPage), pageCount - 1);
        double ratio = (double) clamped / (pageCount - 1);
        return (int) Math.round(ratio * 100.0);
    }


    @Transactional
    public void updateMemberMaxReadPage(Long groupId, Long userId, int newMaxReadPage) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("그룹이 존재하지 않습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저가 존재하지 않습니다."));

        GroupMember gm = groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new NotFoundException("그룹 멤버가 존재하지 않습니다."));

        int pageCount = resolvePageCount(group);
        int clamped = Math.min(Math.max(0, newMaxReadPage), Math.max(0, pageCount - 1));
        if (clamped < gm.getMaxReadPage()) {
            return; // 진도 역행 방지 → 저장하지 않고 종료
        }

        gm.setMaxReadPage(clamped);
        gm.setProgressPercent((float) calcProgressPercent(clamped, pageCount));

        groupMemberRepository.save(gm);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberProgressDto> getGroupAllProgress(Long groupId, Long requesterId) {
        // 보안: 요청자가 해당 그룹 멤버인지 확인
        if (!groupMemberRepository.isMember(groupId, requesterId)) {
            throw new ForbiddenException("해당 그룹 멤버가 아닙니다.");
        }
        return groupMemberRepository.findAllMemberProgressByGroupId(groupId);
    }


    @Override
    public List<CompletedBookDto> getCompletedBooks(Long userId) {
        List<CompletedBookRow> rows = groupMemberRepository.findCompletedBooksByUserIdNative(userId);
        return rows.stream()
                .map(r -> new CompletedBookDto(
                        r.getFileName(),
                        Group.Category.valueOf(r.getCategory())  // 문자열 → Enum
                ))
                .toList();
    }

    private String createContentDispositionHeader(String filename, boolean isAttachment) {
        if (filename == null || filename.trim().isEmpty()) {
            filename = "document.pdf";
        }

        String disposition = isAttachment ? "attachment" : "inline";

        return disposition + "; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8);
    }

}

