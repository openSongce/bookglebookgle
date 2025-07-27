package com.example.bookglebookgleserver.pdf.controller;

import com.example.bookglebookgleserver.pdf.service.PdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pdf")
@Tag(name = "PDF 업로드", description = "PDF 파일 업로드 및 OCR 처리 API") // ✅ Swagger 그룹 이름
public class PdfController {

    private final PdfService pdfService;

    @PostMapping("/upload")
    @Operation(
            summary = "PDF 파일 업로드",
            description = "PDF 파일을 업로드하고 OCR 처리를 요청합니다.",
            security = @SecurityRequirement(name = "bearerAuth") // ✅ Swagger에 JWT 인증 적용
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "403", description = "인증 실패 (토큰 없음/무효)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<Void> uploadPdf(@RequestPart MultipartFile file) {
        System.out.println("✅ PdfController 도달 - 파일 이름: " + file.getOriginalFilename());
        pdfService.handlePdfUpload(file);
        return ResponseEntity.ok().build();
    }
}
