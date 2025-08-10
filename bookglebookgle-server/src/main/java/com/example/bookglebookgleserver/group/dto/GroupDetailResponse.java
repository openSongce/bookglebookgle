package com.example.bookglebookgleserver.group.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "모임방 상세 조회 응답 DTO")
public record GroupDetailResponse(
        @Schema(description = "모임방 이름", example = "문학 읽기 모임")
        String roomTitle,
        @Schema(description = "카테고리", example = "NOVEL")
        String category,
        @Schema(description = "모임 스케줄", example = "매주 금요일 오후 8시")
        String schedule,
        @Schema(description = "현재 참여 인원 수", example = "5")
        int memberCount,
        @Schema(description = "최대 인원 수", example = "8")
        int maxMemberCount,
        @Schema(description = "모임 설명", example = "함께 문학작품을 읽고 토론하는 모임입니다.")
        String description,
        @Schema(description = "모임 대표 사진 URL (현재는 null)", example = "null")
        String photoUrl,
        @Schema(description = "호스트 여부(요청 사용자 기준)", example = "true")
        boolean isHost,
        @Schema(description = "최소 요구 평점", example = "3")
        int minRequiredRating,

        // ✅ 추가
        @Schema(description = "PDF 총 페이지 수", example = "120")
        int pageCount,
        @Schema(description = "멤버 상세 리스트")
        List<GroupMemberDetailDto> members
) {}
