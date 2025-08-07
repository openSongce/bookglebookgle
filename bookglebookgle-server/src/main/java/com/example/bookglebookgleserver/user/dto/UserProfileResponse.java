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

    private int participatedGroups;
}
