package com.example.bookglebookgleserver.group.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "그룹 멤버 상세 정보")
public record GroupMemberDetailDto(
        @Schema(description = "유저 ID", example = "3")
        Long userId,
        @Schema(description = "닉네임", example = "mike")
        String userNickName,
        @Schema(description = "프로필 컬러", example = "#A1B2C3")
        String profileColor,
        @Schema(description = "최대 읽은 페이지(0-based)", example = "45")
        int maxReadPage,
        @Schema(description = "진행 퍼센트(0~100)", example = "38")
        int progressPercent,
        @Schema(description = "방장 여부", example = "false")
        boolean isHost,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
        String profileImageUrl,

        @Schema(description = "이 멤버가 그룹 평점을 제출(완료)했는지", example = "true")
        boolean ratingSubmitted
) {}
