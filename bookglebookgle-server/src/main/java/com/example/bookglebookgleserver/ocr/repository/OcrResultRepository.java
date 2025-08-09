package com.example.bookglebookgleserver.ocr.repository;

import com.example.bookglebookgleserver.ocr.entity.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {
    // pdf_id로 결과 조회
    List<OcrResult> findByPdfFile_PdfId(Long pdfId);
}
