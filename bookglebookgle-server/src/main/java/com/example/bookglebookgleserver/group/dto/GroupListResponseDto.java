package com.example.bookglebookgleserver.group.dto;

import com.example.bookglebookgleserver.group.entity.Group;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupListResponseDto {
    private Long groupId;
    private String roomTitle;
    private String description;
    private String category;
    private int groupMaxNum;
    private int currentNum;
    private int minimumRating;

    public static GroupListResponseDto fromEntity(Group group) {
        return GroupListResponseDto.builder()
                .groupId(group.getId())
                .roomTitle(group.getRoomTitle())
                .description(group.getDescription())
                .category(group.getCategory().name()) // Enum -> String 변환
                .groupMaxNum(group.getGroupMaxNum())
                .currentNum(group.getGroupMembers() != null ? group.getGroupMembers().size() : 0) // 현재 인원
                .minimumRating(group.getMinRequiredRating())
                .build();
    }

}

