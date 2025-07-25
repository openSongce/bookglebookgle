package com.example.bookglebookgleserver.pdf.dto;

public record PdfUploadResponse(
        Long pdfId,
        int ocrCount
) {}
