package com.example.bookglebookgleserver.pdf.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PdfProgressResponse {

    @Schema(description = "그룹 ID", example = "12")
    private Long groupId;

    @Schema(description = "지금까지 사용자가 읽은 최댓값 페이지", example = "37")
    private int maxReadPage;

    @Schema(description = "PDF 전체 페이지 수", example = "240")
    private int totalPages;

    @Schema(description = "진도율(%) 소수점 2자리 반올림", example = "15.42")
    private double progressRate;
}
