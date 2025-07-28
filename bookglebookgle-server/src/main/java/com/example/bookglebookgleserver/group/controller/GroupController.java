package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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
    @PostMapping(value = "/create", consumes = "multipart/form-data")
    public ResponseEntity<?> createGroup(
            @Parameter(description = "그룹 정보 JSON", required = true,
                    content = @Content(schema = @Schema(implementation = GroupCreateRequestDto.class)))
            @RequestPart("groupInfo") GroupCreateRequestDto groupDto,

            @Parameter(description = "PDF 파일", required = true)
            @RequestPart("file") MultipartFile pdfFile,

            @Parameter(description = "JWT 토큰", required = true)
            @RequestHeader("Authorization") String token
    ) {
        groupService.createGroup(groupDto, pdfFile, token);
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
            @RequestHeader("Authorization") String token
    ) {
        groupService.createGroupWithoutOcr(groupDto, pdfFile, token);
        return ResponseEntity.ok("OCR 없이 그룹 생성 완료");
    }
}
