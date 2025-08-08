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
    private double rectX;
    private double rectY;
    private double rectW;
    private double rectH;
}
