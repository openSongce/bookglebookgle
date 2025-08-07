package com.example.bookglebookgleserver.pdf.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PdfProgressResponse {
    private Long groupId;

    private int lastReadPage;

    private int totalPages;

    private double progressRate;
}
