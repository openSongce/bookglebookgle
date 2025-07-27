package com.example.bookglebookgleserver.pdf.controller;

import com.example.bookglebookgleserver.pdf.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pdf")
public class PdfController {

    private final PdfService pdfService;

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadPdf(@RequestPart MultipartFile file) {
        System.out.println("✅ PdfController 도달 - 파일 이름: " + file.getOriginalFilename());
        pdfService.handlePdfUpload(file);
        return ResponseEntity.ok().build();
    }


}
