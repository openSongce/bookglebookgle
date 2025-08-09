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
// @SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final GroupService groupService;

    @Operation(
            summary = "스터디 그룹 생성",
            description = "그룹 정보(JSON)와 PDF 파일(Multipart)을 업로드합니다."
    )
    @ApiResponse(responseCode = "200", description = "생성 성공",
            content = @Content(schema = @Schema(implementation = GroupCreateResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청(파일/필드 오류)")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GroupCreateResponseDto> createGroup(
            @Parameter(description = "그룹 생성 정보(JSON)") @RequestPart("groupInfo") GroupCreateRequestDto dto,
            @Parameter(description = "업로드할 PDF 파일") @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        User user = customUser.getUser();
        GroupCreateResponseDto result = groupService.createGroup(dto, file, user);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "미가입 그룹 목록", description = "아직 가입하지 않은 전체 그룹 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = GroupListResponseDto.class)))
    @GetMapping("/list")
    public ResponseEntity<List<GroupListResponseDto>> getGroupList(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userDetails.getUser();
        return ResponseEntity.ok(groupService.getNotJoinedGroupList(user.getId()));
    }

    @Operation(summary = "그룹 상세", description = "그룹 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = GroupDetailResponse.class)))
    @ApiResponse(responseCode = "404", description = "그룹 없음")
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(
            @Parameter(description = "그룹 ID") @PathVariable("groupId") Long groupId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId, userDetails.getUser()));
    }

    @Operation(summary = "그룹 정보 수정(그룹장)", description = "그룹 제목/설명 등 정보를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = GroupDetailResponse.class)))
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @ApiResponse(responseCode = "404", description = "그룹 없음")
    @PutMapping("/{groupId}/edit")
    public ResponseEntity<GroupDetailResponse> updateGroup(
            @Parameter(description = "그룹 ID") @PathVariable("groupId") Long groupId,
            @RequestBody GroupUpdateRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        return ResponseEntity.ok(groupService.updateGroup(groupId, dto, customUser.getUser()));
    }

    @Operation(
            summary = "그룹 PDF 다운로드",
            description = """
            - 기본: application/pdf 로 PDF만 반환
            - 요청 헤더 Accept: application/zip + hasOcr=true 이면 ZIP(document.pdf + ocr.json) 반환
            """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "다운로드 성공",
                            headers = {
                                    @Header(name = "X-OCR-Available", description = "해당 PDF의 OCR 데이터 보유 여부", schema = @Schema(type = "boolean"))
                            },
                            content = {
                                    @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary")),
                                    @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary"))
                            }
                    ),
                    @ApiResponse(responseCode = "403", description = "권한 없음(미가입)"),
                    @ApiResponse(responseCode = "404", description = "그룹 또는 PDF 없음")
            }
    )
    @GetMapping(value = "/{groupId}/pdf", produces = { MediaType.APPLICATION_PDF_VALUE, "application/zip" })
    public ResponseEntity<?> getGroupPdf(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId,
            @Parameter(
                    in = ParameterIn.HEADER,
                    name = "Accept",
                    description = "원하는 응답 포맷 (application/pdf | application/zip)",
                    schema = @Schema(allowableValues = { MediaType.APPLICATION_PDF_VALUE, "application/zip" }),
                    required = false
            )
            @RequestHeader(value = "Accept", required = false, defaultValue = MediaType.APPLICATION_PDF_VALUE) String accept,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        return groupService.getPdfResponse(groupId, customUser.getUser(), accept);
    }

    @Operation(summary = "내 그룹 목록", description = "로그인 사용자가 참여한 그룹 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = MyGroupSummaryDto.class)))
    @GetMapping("/my")
    public ResponseEntity<List<MyGroupSummaryDto>> getMyGroups(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(groupService.getMyGroupList(userDetails.getUser().getId()));
    }

    @Operation(summary = "그룹 참가", description = "그룹에 참가합니다.")
    @ApiResponse(responseCode = "200", description = "참가 성공")
    @ApiResponse(responseCode = "404", description = "그룹 없음")
    @PostMapping("/{groupId}/join")
    public ResponseEntity<String> joinGroup(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        groupService.joinGroup(groupId, customUser.getUser());
        return ResponseEntity.ok("그룹 참가 완료!");
    }

    @Operation(summary = "그룹 삭제(그룹장)", description = "그룹을 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @ApiResponse(responseCode = "404", description = "그룹 없음")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        groupService.deleteGroup(groupId, userDetails.getUser());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "그룹 검색", description = "방 제목과 카테고리로 그룹을 검색합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = GroupListResponseDto.class)))
    @GetMapping("/search")
    public ResponseEntity<List<GroupListResponseDto>> searchGroups(
            @Parameter(description = "방 제목(부분 일치)") @RequestParam(required = false) String roomTitle,
            @Parameter(description = "카테고리(정확 일치)") @RequestParam(required = false) String category) {
        return ResponseEntity.ok(groupService.searchGroups(roomTitle, category));
    }

    @Operation(summary = "모임 탈퇴", description = "해당 그룹에서 탈퇴합니다.")
    @ApiResponse(responseCode = "204", description = "탈퇴 성공")
    @ApiResponse(responseCode = "404", description = "그룹 없음")
    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        groupService.leaveGroup(groupId, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "마지막으로 본 페이지 조회", description = "특정 참여자의 마지막 열람 페이지를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = LastPageReadResponseDto.class)))
    @ApiResponse(responseCode = "403", description = "본인만 조회 가능")
    @GetMapping("/{groupId}/members/{userId}/last-page")
    public ResponseEntity<LastPageReadResponseDto> getLastPageRead(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId,
            @Parameter(description = "사용자 ID(본인)") @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!userDetails.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인만 조회할 수 있습니다.");
        }
        return ResponseEntity.ok(new LastPageReadResponseDto(groupService.getLastPageRead(groupId, userId)));
    }
}
