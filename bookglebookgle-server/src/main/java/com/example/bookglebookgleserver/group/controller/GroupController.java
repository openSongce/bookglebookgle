package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.group.dto.GroupCreateRequestDto;
import com.example.bookglebookgleserver.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/group")
public class GroupController {

    private final GroupService groupService;

    @Operation(
            summary = "스터디 그룹 생성",
            description = "PDF와 그룹 정보를 업로드하여 스터디 그룹을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스터디 그룹 생성 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 에러")
    })
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createGroup(
            @Parameter(
                    description = "스터디 그룹 생성 정보 (JSON)",
                    required = true,
                    content = @Content(schema = @Schema(implementation = GroupCreateRequestDto.class))
            )
            @RequestPart("groupInfo") GroupCreateRequestDto groupDto,

            @Parameter(description = "PDF 파일", required = true)
            @RequestPart("file") MultipartFile pdfFile,

            @Parameter(description = "Access Token", required = true, example = "Bearer {JWT}")
            @RequestHeader("Authorization") String token
    ) {
        groupService.createGroup(groupDto, pdfFile, token);
        return ResponseEntity.ok("스터디 그룹 생성 완료");
    }

    @Operation(
            summary = "OCR 없이 스터디 그룹 생성",
            description = "PDF와 그룹 정보를 업로드하여 OCR 없이 그룹을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OCR 없이 그룹 생성 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 에러")
    })
    @PostMapping(value = "/create/no-ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createGroupWithoutOcr(
            @Parameter(
                    description = "스터디 그룹 생성 정보 (JSON)",
                    required = true,
                    content = @Content(schema = @Schema(implementation = GroupCreateRequestDto.class))
            )
            @RequestPart("groupInfo") GroupCreateRequestDto groupDto,

            @Parameter(description = "PDF 파일", required = true)
            @RequestPart("file") MultipartFile pdfFile,

            @Parameter(description = "Access Token", required = true, example = "Bearer {JWT}")
            @RequestHeader("Authorization") String token
    ) {
        groupService.createGroupWithoutOcr(groupDto, pdfFile, token);
        return ResponseEntity.ok("OCR 없이 그룹 생성 완료");
    }
}
