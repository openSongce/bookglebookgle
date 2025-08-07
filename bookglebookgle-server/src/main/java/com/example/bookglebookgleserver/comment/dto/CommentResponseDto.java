package com.example.bookglebookgleserver.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "PDF 주석(Comment) 응답 DTO")
public record CommentResponseDto(
        @Schema(description = "댓글 ID", example = "11")
        Long id,

        @Schema(description = "그룹 ID", example = "1")
        Long groupId,

        @Schema(description = "유저 ID", example = "7")
        Long userId,

        @Schema(description = "페이지 번호", example = "3")
        int page,

        @Schema(description = "주석 대상 텍스트")
        String snippet,

        @Schema(description = "실제 댓글 내용")
        String text,

        @Schema(description = "시작 X좌표", example = "0.51")
        double startX,
        @Schema(description = "시작 Y좌표", example = "0.51")
        double startY,
        @Schema(description = "끝 X좌표", example = "0.99")
        double endX,
        @Schema(description = "끝 Y좌표", example = "0.99")
        double endY,

        @Schema(description = "생성일시", example = "2025-08-07T12:34:56")
        String createdAt
) {}
