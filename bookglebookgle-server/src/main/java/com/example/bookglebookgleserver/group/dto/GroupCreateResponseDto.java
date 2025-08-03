package com.example.bookglebookgleserver.group.dto;

import com.example.bookglebookgleserver.ocr.dto.OcrTextBlockDto;
import lombok.*;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupCreateResponseDto {
    private Long groupId;
    private Long pdfId;
    private List<OcrTextBlockDto> ocrResultlist;
}
