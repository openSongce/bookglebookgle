package com.example.bookglebookgleserver.user.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class UserProfileResponse {
    private String email;
    private String nickname;
    private String profileImgUrl;
    private double avgRating;
    private int totalActiveHours;
    private int completedGroups;
    private int incompleteGroups;
    private double progressPercent;
    private String profileColor;   // "#RRGGBB" 또는 "#AARRGGBB"
    private int participatedGroups;

    private String prettyActiveTime;  // 예: "12h 05m" 또는 "12시간 05분"
    private long totalActiveSeconds;  // (옵션) 프론트 포맷용 원본 초
}
