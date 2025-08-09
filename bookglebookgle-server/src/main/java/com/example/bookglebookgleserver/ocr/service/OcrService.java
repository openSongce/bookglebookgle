package com.example.bookglebookgleserver.ocr.service;

import com.bgbg.ai.grpc.AIServiceProto.ProcessPdfResponse;
import com.bgbg.ai.grpc.AIServiceProto.TextBlock;
import com.example.bookglebookgleserver.ocr.dto.OcrTextBlockDto;
import com.example.bookglebookgleserver.ocr.entity.OcrResult;
import com.example.bookglebookgleserver.ocr.repository.OcrResultRepository;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OcrService {

    private final OcrResultRepository ocrResultRepository;

    @Transactional
    public void saveOcrResults(PdfFile pdfFile, ProcessPdfResponse response) {
        List<OcrResult> results = new ArrayList<>();

        for (TextBlock block : response.getTextBlocksList()) {
            OcrResult result = OcrResult.builder()
                    .pdfFile(pdfFile)
                    .pageNumber(block.getPageNumber())
                    .text(block.getText())
                    .rectX((float) block.getX0())
                    .rectY((float) block.getY0())
                    .rectW((float) (block.getX1() - block.getX0()))
                    .rectH((float) (block.getY1() - block.getY0()))
                    .build();
            results.add(result);
        }

        ocrResultRepository.saveAll(results);
    }

    // ✅ 추가: pdfId로 OCR 블록 조회 (GroupServiceImpl의 ZIP 응답에서 사용)
    @Transactional(readOnly = true)
    public List<OcrTextBlockDto> getOcrBlocksByPdfId(Long pdfId) {
        return ocrResultRepository.findByPdfFile_PdfId(pdfId).stream()
                .map(e -> OcrTextBlockDto.builder()
                        .pageNumber(e.getPageNumber())
                        .text(e.getText())
                        .rectX(e.getRectX())
                        .rectY(e.getRectY())
                        .rectW(e.getRectW())
                        .rectH(e.getRectH())
                        .build())
                .toList();
    }
}
