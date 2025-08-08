package com.example.bookglebookgleserver.user;



import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.user.dto.UserProfileResponse;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.PdfViewingSessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final GroupMemberRepository groupMemberRepository;
    private final PdfViewingSessionRepository pdfViewingSessionRepository;

    @Operation(summary = "사용자 프로필 조회", description = "이메일, 닉네임, 프로필 사진, 평점, 참여/완료/미완료 모임 수, 총 활동 시간 반환")
    @GetMapping("/profile")
    public UserProfileResponse getProfile(@AuthenticationPrincipal User currentUser) {
        Long userId = currentUser.getId();

        // 평균 별점
        double avgRating = currentUser.getAvgRating() != null ? currentUser.getAvgRating() : 0.0f;

        // 참가한 모임 수
        int participated = groupMemberRepository.countJoinedGroupsByUserId(userId);
        int completed = groupMemberRepository.countCompletedGroupsByUserId(userId);
        int incomplete = groupMemberRepository.countIncompleteGroupsByUserId(userId);

        // 총 열람 시간 (초 -> 시간)
        Long totalSeconds = pdfViewingSessionRepository.sumTotalViewingTimeByUserId(userId);
        int totalHours = totalSeconds == null ? 0 : (int) (totalSeconds / 3600);

        return UserProfileResponse.builder()
                .email(currentUser.getEmail())
                .nickname(currentUser.getNickname())
                .profileImgUrl(currentUser.getProfileImageUrl())
                .avgRating(avgRating)
                .participatedGroups(participated)
                .completedGroups(completed)
                .incompleteGroups(incomplete)
                .totalActiveHours(totalHours)
                .build();
    }

}
