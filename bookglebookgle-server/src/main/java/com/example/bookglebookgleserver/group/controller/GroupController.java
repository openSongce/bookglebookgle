package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.global.exception.ForbiddenException;
import com.example.bookglebookgleserver.group.dto.*;
import com.example.bookglebookgleserver.group.service.GroupService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/group")
@Tag(name = "스터디 그룹", description = "스터디 그룹 관련 API")
public class GroupController {

    private final GroupService groupService;

    @Operation(summary = "스터디 그룹 생성 (OCR 포함 여부 설정 가능)")
    @PostMapping("/create")
    public ResponseEntity<GroupCreateResponseDto> createGroup(
            @RequestPart("groupInfo") GroupCreateRequestDto dto,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        User user = customUser.getUser();
        GroupCreateResponseDto result = groupService.createGroup(dto, file, user);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/list")
    @Operation(summary = "미가입 그룹 전체 목록 조회")
    public ResponseEntity<List<GroupListResponseDto>> getGroupList(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userDetails.getUser();
        return ResponseEntity.ok(groupService.getNotJoinedGroupList(user.getId()));
    }

    @Operation(
            summary = "스터디 그룹 상세 조회",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = GroupDetailResponse.class))),
                    @ApiResponse(responseCode = "404", description = "해당 그룹이 존재하지 않음")
            }
    )
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(
            @PathVariable("groupId") Long groupId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId, userDetails.getUser()));
    }

    @Operation(summary = "스터디 그룹 정보 수정(그룹장 전용)")
    @PutMapping("/{groupId}/edit")
    public ResponseEntity<GroupDetailResponse> updateGroup(
            @PathVariable("groupId") Long groupId,
            @RequestBody GroupUpdateRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        return ResponseEntity.ok(groupService.updateGroup(groupId, dto, customUser.getUser()));
    }

    @Operation(
            summary = "그룹 PDF 다운로드 (필요 시 OCR 결과 동시 제공)",
            description = """
            - 기본: application/pdf 로 PDF만 반환
            - Accept: application/zip + hasOcr=true 이면 ZIP(document.pdf + ocr.json) 반환
            """
    )
    @ApiResponse(
            responseCode = "200",
            description = "PDF 또는 ZIP 다운로드 성공",
            headers = {
                    @Header(name = "X-OCR-Available", description = "해당 PDF의 OCR 데이터 보유 여부", schema = @Schema(implementation = Boolean.class))
            },
            content = {
                    @Content(
                            mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    ),
                    @Content(
                            mediaType = "application/zip",
                            schema = @Schema(type = "string", format = "binary")
                    )
            }
    )
    @ApiResponse(responseCode = "403", description = "다운로드 권한 없음(그룹 미가입)")
    @ApiResponse(responseCode = "404", description = "해당 그룹 또는 PDF가 존재하지 않음")
    @GetMapping(value = "/{groupId}/pdf", produces = { MediaType.APPLICATION_PDF_VALUE, "application/zip" })
    public ResponseEntity<?> getGroupPdf(
            @PathVariable Long groupId,
            @Parameter(
                    in = ParameterIn.HEADER,
                    name = "Accept",
                    description = "원하는 응답 포맷",
                    schema = @Schema(allowableValues = { MediaType.APPLICATION_PDF_VALUE, "application/zip" }),
                    required = false
            )
            @RequestHeader(value = "Accept", required = false, defaultValue = MediaType.APPLICATION_PDF_VALUE) String accept,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        return groupService.getPdfResponse(groupId, customUser.getUser(), accept);
    }

    @GetMapping("/my")
    @Operation(summary = "내가 속한 그룹 목록 조회")
    public ResponseEntity<List<MyGroupSummaryDto>> getMyGroups(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(groupService.getMyGroupList(userDetails.getUser().getId()));
    }

    @Operation(summary = "스터디 그룹 참가")
    @PostMapping("/{groupId}/join")
    public ResponseEntity<String> joinGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        groupService.joinGroup(groupId, customUser.getUser());
        return ResponseEntity.ok("그룹 참가 완료!");
    }

    @Operation(summary = "스터디 그룹 삭제(그룹장 전용)")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        groupService.deleteGroup(groupId, userDetails.getUser());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    @Operation(summary = "그룹 검색", description = "방 제목과 카테고리로 그룹을 검색합니다.")
    public ResponseEntity<List<GroupListResponseDto>> searchGroups(
            @RequestParam(required = false) String roomTitle,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(groupService.searchGroups(roomTitle, category));
    }

    @DeleteMapping("/{groupId}/leave")
    @Operation(summary = "모임 탈퇴", description = "로그인한 유저가 해당 그룹에서 탈퇴합니다.")
    public ResponseEntity<Void> leaveGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        groupService.leaveGroup(groupId, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "참여자의 마지막으로 본 페이지 조회")
    @GetMapping("/{groupId}/members/{userId}/last-page")
    public ResponseEntity<LastPageReadResponseDto> getLastPageRead(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!userDetails.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인만 조회할 수 있습니다.");
        }
        return ResponseEntity.ok(new LastPageReadResponseDto(groupService.getLastPageRead(groupId, userId)));
    }
}
