package com.example.bookglebookgleserver.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CommentResponseDto(
        @Schema(description = "댓글 ID", example = "10") Long id,
        @Schema(description = "PDF 파일 ID", example = "2") Long pdfFileId,
        @Schema(description = "그룹 ID", example = "1") Long groupId,
        @Schema(description = "작성자 유저 ID", example = "3") Long userId,
        @Schema(description = "PDF 페이지 번호", example = "4") int page,
        @Schema(description = "주석 텍스트", example = "중요 부분") String snippet,
        @Schema(description = "댓글 내용", example = "여기 다시 볼 것!") String text,
        @Schema(description = "선택영역 시작 X", example = "0.1234") double startX,
        @Schema(description = "선택영역 시작 Y", example = "0.1234") double startY,
        @Schema(description = "선택영역 끝 X", example = "0.1234") double endX,
        @Schema(description = "선택영역 끝 Y", example = "0.1234") double endY,
        @Schema(description = "생성 일시", example = "2025-08-06T23:55:34.260Z") String createdAt
) {}
