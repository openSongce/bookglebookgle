package com.example.bookglebookgleserver.ocr.service;

import com.bgbg.ai.grpc.ProcessPdfResponse;
import com.bgbg.ai.grpc.TextBlock;
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
}
