package com.example.bookglebookgleserver.highlight.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "PDF 하이라이트(형광펜) 응답 DTO")
public record HighlightResponseDto(
        @Schema(description = "하이라이트 ID", example = "101")
        Long id,

        @Schema(description = "그룹 ID", example = "1")
        Long groupId,

        @Schema(description = "유저 ID", example = "7")
        Long userId,

        @Schema(description = "페이지 번호", example = "3")
        int page,

        @Schema(description = "하이라이트된 텍스트")
        String snippet,

        @Schema(description = "하이라이트 색상", example = "yellow")
        String color,

        @Schema(description = "시작 X좌표", example = "0.511234")
        double startX,
        @Schema(description = "시작 Y좌표", example = "0.511234")
        double startY,
        @Schema(description = "끝 X좌표", example = "0.511234")
        double endX,
        @Schema(description = "끝 Y좌표", example = "0.0124")
        double endY,
        @Schema(description = "하이라이트 생성일시", example = "2025-08-07T12:34:56")
        String createdAt
) {}


