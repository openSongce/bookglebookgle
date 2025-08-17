package com.example.bookglebookgleserver.pdf.util;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;

public class PdfUtils {
    public static int getPageCount(String filePath) {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new RuntimeException("PDF 페이지 수를 읽는 데 실패했습니다: " + filePath, e);
        }
    }
}
