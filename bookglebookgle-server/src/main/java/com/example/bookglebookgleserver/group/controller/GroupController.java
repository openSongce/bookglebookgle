package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.dto.GroupDetailResponse;
import com.example.bookglebookgleserver.group.dto.GroupListResponseDto;
import com.example.bookglebookgleserver.group.service.GroupService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<?> createGroup(
            @RequestPart("groupInfo") GroupCreateRequestDto dto,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails customUser  // ✅ 여기만 바꾸면 됨
    ) {
        User user = customUser.getUser();  // ✅ User 엔티티 직접 접근 가능
        groupService.createGroup(dto, file, user);
        return ResponseEntity.ok("스터디 그룹 생성 완료");
    }

    @Operation(
            summary = "OCR 없이 스터디 그룹 생성",
            description = "PDF 파일을 업로드하되 OCR 처리를 하지 않고 그룹을 생성합니다."
    )
    @PostMapping("/create/no-ocr")
    public ResponseEntity<?> createGroupWithoutOcr(
            @RequestPart("groupInfo") GroupCreateRequestDto groupDto,
            @RequestPart("file") MultipartFile pdfFile,
            @AuthenticationPrincipal CustomUserDetails customUser  // ✅ 변경
    ) {
        User user = customUser.getUser();  // ✅ User 추출
        groupService.createGroupWithoutOcr(groupDto, pdfFile, user);  // ✅ User 넘김
        return ResponseEntity.ok("OCR 없이 그룹 생성 완료");
    }

    @GetMapping("/list")
    public ResponseEntity<List<GroupListResponseDto>> getGroupList() {
        List<GroupListResponseDto> groupList = groupService.getGroupList();
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
    public ResponseEntity<GroupDetailResponse> getGroupDetail(@PathVariable("groupId") Long groupId) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId));
    }
//
//    @PutMapping("/{groupId}/edit")
//    public ResponseEntity<GroupUpdateRequestDto> updateGroup(
//            @PathVariable("groupId") Long groupId,
//            @RequestBody GroupUpdateRequestDto groupDto) {
//
//    }
}
