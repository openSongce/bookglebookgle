package com.example.bookglebookgleserver.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "스터디 그룹 생성 요청 DTO")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GroupCreateRequestDto {

    @Schema(description = "모임 제목", example = "7월 독서모임")
    private String roomTitle;

    @Schema(description = "설명", example = "함께 책 읽고 토론해요")
    private String description;

    @Schema(description = "카테고리", example = "READING")
    private String category;

    @Schema(description = "최소 평점", example = "3")
    private int minRequiredRating;

    @Schema(description = "일정", example = "2025-08-01T20:00:00")
    private String schedule;

    @Schema(description = "최대 인원", example = "6")
    private int groupMaxNum;

    @Schema(description = "읽기 방식", example = "FOLLOW")
    private String readingMode;

    @Schema(
            name = "imageBased", // JSON 필드명으로 Swagger가 인식하게 함
            description = "이미지 기반인지 여부",
            example = "false"
    )
    @JsonProperty("imageBased")
    private boolean imageBased;
}
