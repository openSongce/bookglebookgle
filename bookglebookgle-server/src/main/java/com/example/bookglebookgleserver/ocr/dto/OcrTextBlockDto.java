package com.example.bookglebookgleserver.ocr.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OcrTextBlockDto {
    private int pageNumber;
    private String text;
    private int rectX;
    private int rectY;
    private int rectW;
    private int rectH;
}
