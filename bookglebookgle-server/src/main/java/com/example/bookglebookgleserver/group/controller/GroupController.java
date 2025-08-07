package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.global.exception.ForbiddenException;
import com.example.bookglebookgleserver.group.dto.*;
import com.example.bookglebookgleserver.group.service.GroupService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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

    @Operation(
            summary = "스터디 그룹 생성 (OCR 포함 여부 설정 가능)",
            description = """
                PDF와 그룹 정보를 함께 업로드하여 스터디 그룹을 생성합니다.
                - `imageBased`가 true인 경우 OCR 서버로 전송됩니다.
                - JWT 토큰 필요 (Authorization 헤더에 Bearer 토큰 포함)
                """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "스터디 그룹 생성 성공"),
                    @ApiResponse(responseCode = "400", description = "요청 값 오류"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
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


//    @Operation(
//            summary = "OCR 없이 스터디 그룹 생성",
//            description = "PDF 파일을 업로드하되 OCR 처리를 하지 않고 그룹을 생성합니다."
//    )
//    @PostMapping("/create/no-ocr")
//    public ResponseEntity<?> createGroupWithoutOcr(
//            @RequestPart("groupInfo") GroupCreateRequestDto groupDto,
//            @RequestPart("file") MultipartFile pdfFile,
//            @AuthenticationPrincipal CustomUserDetails customUser  // ✅ 변경
//    ) {
//        User user = customUser.getUser();  // ✅ User 추출
//        groupService.createGroupWithoutOcr(groupDto, pdfFile, user);  // ✅ User 넘김
//        return ResponseEntity.ok("OCR 없이 그룹 생성 완료");
//    }

    @GetMapping("/list")
    @Operation(
            summary = "미가입 그룹 전체 목록 조회",
            description = "현재 로그인한 사용자가 가입되어 있지 않은 스터디 그룹 전체 목록을 조회합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    public ResponseEntity<List<GroupListResponseDto>> getGroupList(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userDetails.getUser();
        List<GroupListResponseDto> groupList = groupService.getNotJoinedGroupList(user.getId());
        return ResponseEntity.ok(groupList);
    }

    @Operation(
            summary = "스터디 그룹 상세 조회",
            description = "groupId를 기반으로 해당 스터디 그룹의 상세 정보를 조회합니다.",
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
        User user = userDetails.getUser();
        GroupDetailResponse response = groupService.getGroupDetail(groupId, user);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "스터디 그룹 정보 수정",
            description = """
        특정 그룹의 정보를 수정합니다.<br>
        - 그룹장만 수정 가능<br>
        - JWT 인증 필요 (Authorization 헤더)
        """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "수정 성공",
                            content = @Content(schema = @Schema(implementation = GroupDetailResponse.class))
                    ),
                    @ApiResponse(responseCode = "403", description = "수정 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "해당 그룹이 존재하지 않음"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    @PutMapping("/{groupId}/edit")
    public ResponseEntity<GroupDetailResponse> updateGroup(
            @PathVariable("groupId") Long groupId,
            @RequestBody GroupUpdateRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        User user = customUser.getUser();
        GroupDetailResponse result = groupService.updateGroup(groupId, dto, user);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "그룹 PDF 파일 다운로드",
            description = """
        해당 그룹의 PDF 파일을 다운로드합니다.<br>
        - 그룹원만 다운로드 가능<br>
        - JWT 인증 필요 (Authorization 헤더)
        """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "PDF 다운로드 성공"),
                    @ApiResponse(responseCode = "403", description = "다운로드 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "해당 그룹 또는 PDF가 존재하지 않음"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    @GetMapping("/{groupId}/pdf")
    public ResponseEntity<Resource> getGroupPdf(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        User user = customUser.getUser();
        return groupService.getPdfFileResponse(groupId, user);
    }

    @Operation(
            summary = "내가 속한 그룹 목록 조회",
            description = """
        현재 로그인한 사용자가 속한 모든 스터디 그룹의 요약 정보를 조회합니다.<br>
        - JWT 인증 필요 (Authorization 헤더)
        """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = MyGroupSummaryDto.class))
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 필요"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    @GetMapping("/my")
    public ResponseEntity<List<MyGroupSummaryDto>> getMyGroups(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(groupService.getMyGroupList(userId));
    }

    @Operation(
            summary = "스터디 그룹 참가",
            description = """
        해당 groupId의 스터디 그룹에 참가합니다.<br>
        - JWT 인증 필요 (Authorization 헤더)
        """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "참가 성공"),
                    @ApiResponse(responseCode = "400", description = "이미 참가한 그룹이거나 기타 오류"),
                    @ApiResponse(responseCode = "404", description = "해당 그룹이 존재하지 않음"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    @PostMapping("/{groupId}/join")
    public ResponseEntity<String> joinGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        User user = customUser.getUser();
        groupService.joinGroup(groupId, user);
        return ResponseEntity.ok("그룹 참가 완료!");
    }

    @Operation(
            summary = "스터디 그룹 삭제",
            description = """
        그룹 ID를 받아 해당 그룹을 삭제합니다. <br>
        - JWT 인증 필요 (Authorization 헤더, Bearer 토큰)
        - 그룹장만 삭제 가능
        - 삭제 시 연관된 PDF/댓글/하이라이트 등도 모두 삭제됩니다.
        """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "삭제 성공"),
                    @ApiResponse(responseCode = "403", description = "삭제 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "해당 그룹이 존재하지 않음"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
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
        List<GroupListResponseDto> groups = groupService.searchGroups(roomTitle, category);
        return ResponseEntity.ok(groups);
    }

    @DeleteMapping("/{groupId}/leave")
    @Operation(summary = "모임 탈퇴", description = "로그인한 유저가 해당 그룹에서 탈퇴합니다.")
    public ResponseEntity<Void> leaveGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        groupService.leaveGroup(groupId, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "참여자의 마지막으로 본 페이지 조회",
            description = "해당 그룹에서 본인이 마지막으로 확인한 페이지(DB 기준)를 조회한다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = LastPageReadResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "인증 필요"),
                    @ApiResponse(responseCode = "403", description = "본인만 조회 가능")
            }
    )

    @GetMapping("/{groupId}/members/{userId}/last-page")
    public ResponseEntity<LastPageReadResponseDto> getLastPageRead(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!userDetails.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인만 조회할 수 있습니다.");
        }
        int page = groupService.getLastPageRead(groupId, userId);
        return ResponseEntity.ok(new LastPageReadResponseDto(page));
    }
}
