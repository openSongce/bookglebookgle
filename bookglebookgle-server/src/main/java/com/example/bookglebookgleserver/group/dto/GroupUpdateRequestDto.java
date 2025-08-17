package com.example.bookglebookgleserver.group.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupUpdateRequestDto {
    private String roomTitle;
    private String description;
    private String category;
    private int minRequiredRating;
    private String schedule;
    private int groupMaxNum;
    private String readingMode;
    private boolean imageBased;
}
