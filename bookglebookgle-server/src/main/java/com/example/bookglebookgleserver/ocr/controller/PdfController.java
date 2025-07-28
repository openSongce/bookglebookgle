package com.example.bookglebookgleserver.ocr.controller;

import com.bgbg.ai.grpc.ProcessPdfResponse;
import com.example.bookglebookgleserver.auth.service.JwtService;
import com.example.bookglebookgleserver.ocr.grpc.GrpcOcrClient;
import com.example.bookglebookgleserver.ocr.service.OcrService;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.repository.PdfFileRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pdf")
public class PdfController {

    private final JwtService jwtService; // ✅ JwtService로 변경
    private final UserRepository userRepository;
    private final PdfFileRepository pdfFileRepository;
    private final GrpcOcrClient grpcOcrClient;
    private final OcrService ocrService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPdf(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {

        // ✅ "Bearer " 접두어 제거 후 이메일 추출
        String token = authHeader.replace("Bearer ", "");
        String email = jwtService.extractEmailFromAccessToken(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        // 1. PDF 엔티티 저장
        PdfFile pdfFile = PdfFile.builder()
                .uploadUser(user)
                .fileName(file.getOriginalFilename())
                .build();
        pdfFileRepository.save(pdfFile);

        // 2. OCR 요청 (gRPC)
        ProcessPdfResponse response = grpcOcrClient.sendPdf(pdfFile.getPdfId(), file);

        // 3. OCR 결과 저장
        ocrService.saveOcrResults(pdfFile, response);

        return ResponseEntity.ok("PDF 업로드 및 OCR 완료");
    }
}
