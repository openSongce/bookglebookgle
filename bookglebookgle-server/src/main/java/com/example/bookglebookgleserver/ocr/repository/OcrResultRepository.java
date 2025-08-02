package com.example.bookglebookgleserver.ocr.repository;

import com.example.bookglebookgleserver.ocr.entity.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {
    // pdfId 기준으로 OCR 결과 조회 등 필요한 메서드는 나중에 추가
}
