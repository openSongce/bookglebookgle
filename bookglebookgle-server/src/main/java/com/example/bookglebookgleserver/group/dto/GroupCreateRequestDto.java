package com.example.bookglebookgleserver.group.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GroupCreateRequestDto {

    private String roomTitle;
    private String description;
    private String category;
    private int minRequiredRating;
    private String schedule;
    private int groupMaxNum;
    private String readingMode;
    private boolean imageBased;
    private int totalPages;
}
