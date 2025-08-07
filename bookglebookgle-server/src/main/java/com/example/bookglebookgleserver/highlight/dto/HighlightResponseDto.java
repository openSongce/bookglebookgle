package com.example.bookglebookgleserver.highlight.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HighlightResponseDto(
        @Schema(description = "하이라이트 ID", example = "10") Long id,
        @Schema(description = "그룹 ID", example = "1") Long groupId,
        @Schema(description = "작성자 유저 ID", example = "3") Long userId,
        @Schema(description = "PDF 페이지 번호", example = "4") int page,
        @Schema(description = "하이라이트된 텍스트", example = "중요 부분") String snippet,
        @Schema(description = "형광펜 색상", example = "#FFEB3B") String color,
        @Schema(description = "하이라이트 시작 X", example = "100") double startX,
        @Schema(description = "하이라이트 시작 Y", example = "150") double startY,
        @Schema(description = "하이라이트 끝 X", example = "300") double endX,
        @Schema(description = "하이라이트 끝 Y", example = "200") double endY,
        @Schema(description = "PDF 파일 ID", example = "2") Long pdfId,
        @Schema(description = "PDF 파일명", example = "sample.pdf") String pdfFileName,
        @Schema(description = "생성 일시", example = "2025-08-06T23:55:34.260Z") String createdAt
) {}

