package com.example.bookglebookgleserver.user.service;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.user.dto.UserProfileResponse;
import com.example.bookglebookgleserver.user.entity.PdfViewingSession;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.PdfViewingSessionRepository;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;



@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PdfViewingSessionRepository pdfViewingSessionRepository;

    private final ViewingSessionService viewingSessionService;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();



        // 1) users 테이블 값으로 조회
        long totalSecFromUsers = (user.getTotalActiveSeconds() == null ? 0L : user.getTotalActiveSeconds());

        // 2) 혹시 컬럼이 0이고, 세션 합계가 필요한 경우에만 fallback 하고 싶다면:
        long totalSec = totalSecFromUsers;
//  if (totalSecFromUsers == 0L) {
//      totalSec = getTotalViewingSeconds(userId); // fallback
//  }

        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;

        String pretty = (hours == 0)
                ? String.format("%d분", minutes)
                : String.format("%d시간 %02d분", hours, minutes);

        int completed   = groupMemberRepository.countCompletedGroupsByUserId(userId);
        int incomplete  = groupMemberRepository.countIncompleteGroupsByUserId(userId);
        int participated= groupMemberRepository.countJoinedGroupsByUserId(userId);

        return UserProfileResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImgUrl(user.getProfileImageUrl())
                .avgRating(user.getAvgRating() == null ? 0.0 : user.getAvgRating())
                .totalActiveHours((int) hours)      // 또는 user.getTotalActiveHours() (생성 컬럼이면)
                .prettyActiveTime(pretty)
                .totalActiveSeconds(totalSec)
                .completedGroups(completed)
                .incompleteGroups(incomplete)
                .participatedGroups(participated)
                .profileColor(user.getProfileColor())
                .progressPercent(0.0)
                .build();

////        int totalActiveHours = viewingSessionService.getTotalActiveHoursFloor(userId);
//        int completed = groupMemberRepository.countCompletedGroupsByUserId(userId);
//        int incomplete = groupMemberRepository.countIncompleteGroupsByUserId(userId);
//        int participated = groupMemberRepository.countJoinedGroupsByUserId(userId);
//
//        // TODO: progressPercent 산식이 따로 있으면 여기에 계산/조회
//        double progressPercent = 0.0;
//
//        long totalSec = (user.getTotalActiveSeconds() == null ? 0L : user.getTotalActiveSeconds());
//        long hours = totalSec / 3600;
//        long minutes = (totalSec % 3600) / 60;
//
//        String pretty = (hours == 0) ? String.format("%d분", minutes)
//                : String.format("%d시간 %02d분", hours, minutes);
//
//        return UserProfileResponse.builder()
//                .email(user.getEmail())
//                .nickname(user.getNickname())
//                .profileImgUrl(user.getProfileImageUrl())
//                .avgRating(user.getAvgRating() == null ? 0.0 : user.getAvgRating())
//                .totalActiveHours((int)hours)
//                .prettyActiveTime(pretty)
//                .totalActiveSeconds(totalSec)
//                .completedGroups(completed)
//                .incompleteGroups(incomplete)
//                .participatedGroups(participated)
//                .progressPercent(progressPercent)
//                .profileColor(user.getProfileColor())
//                .build();
    }
}
