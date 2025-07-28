package com.example.bookglebookgleserver.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class GroupCreateRequestDto {
    private String roomTitle;
    private String description;
    private String category;
    private int minRequiredRating;
    private LocalDateTime schedule;
    private int groupMaxNum;
    private String readingMode;

    // OCR 필요 여부 플래그
    private boolean isImageBased; // ← 여기만 있어도 충분함
}

