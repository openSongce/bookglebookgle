package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.global.exception.ForbiddenException;
import com.example.bookglebookgleserver.group.dto.LeaderPageResponseDto;
import com.example.bookglebookgleserver.group.dto.UpdateLeaderPageRequestDto;
import com.example.bookglebookgleserver.group.service.GroupService;
import com.example.bookglebookgleserver.group.service.GroupSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/group/sync")
@Tag(name = "스터디 그룹 실시간 동기화", description = "스터디 그룹 리더-참여자 실시간 동기화 API")
public class GroupSyncController {

    private final GroupSyncService groupSyncService;
    private final GroupService groupService;

    @Operation(
            summary = "리더 페이지 업데이트",
            description = "그룹 리더가 페이지를 넘길 때 현재 페이지를 저장합니다."
    )
    @PostMapping("/{groupId}/leader-page")
    public ResponseEntity<?> updateLeaderPage(
            @PathVariable Long groupId,
            @RequestBody UpdateLeaderPageRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        Long userId = customUser.getUser().getId();
        if (!groupService.isLeader(groupId, userId)) { // 리더만 허용 예시
            throw new ForbiddenException("리더만 페이지를 변경할 수 있습니다.");
        }
        groupSyncService.updateLeaderPage(groupId, dto.page());
        return ResponseEntity.ok().body("{\"success\":true}");
    }

    @Operation(
            summary = "리더 페이지 조회",
            description = "현재 그룹 리더가 보고 있는 페이지를 반환합니다. (참여자 '따라가기' 모드에서 사용)"
    )
    @GetMapping("/{groupId}/leader-page")
    public ResponseEntity<LeaderPageResponseDto> getLeaderPage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        Long userId = customUser.getUser().getId();
        if (!groupService.isMember(groupId, userId)) {
            throw new ForbiddenException("그룹 멤버만 리더 페이지를 조회할 수 있습니다.");
        }
        int page = groupSyncService.getLeaderPage(groupId);
        return ResponseEntity.ok(new LeaderPageResponseDto(page));
    }
}
