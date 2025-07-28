package com.example.bookglebookgleserver.pdf.controller;

import com.example.bookglebookgleserver.global.exception.dto.ErrorResponse;
import com.example.bookglebookgleserver.pdf.service.PdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pdf")
@Tag(name = "PDF 업로드", description = "PDF 파일 업로드 및 OCR 처리 API")
@Slf4j
public class PdfController {

    private final PdfService pdfService;

    @PostMapping("/upload")
    @Operation(
            summary = "PDF 파일 업로드",
            description = "PDF 파일을 업로드하고 OCR 처리를 요청합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 없음 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "유저 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> uploadPdf(@RequestPart MultipartFile file) {
        log.info("✅ PdfController 도달 - 파일 이름: {}", file.getOriginalFilename());
        pdfService.handlePdfUpload(file);
        return ResponseEntity.ok().build();
    }
}
