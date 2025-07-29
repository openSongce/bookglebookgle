package com.example.bookglebookgleserver.group.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder // ✅ 이게 빠졌을 가능성이 높습니다
public class GroupListResponseDto {
    private Long groupId;
    private String roomTitle;
    private String description;
    private String category;
    private int groupMaxNum;
    private int currentNum;
    private int minimumRating;
}

