package com.example.bookglebookgleserver.pdf.dto;

public record OcrResultDto(
        int pageNumber,
        int lineNumber,
        int wordIndex,
        String text,
        float rectX,
        float rectY,
        float rectW,
        float rectH
) {}
