package com.example.bookglebookgleserver.user.service;


import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.user.dto.UserProfileResponse;
import com.example.bookglebookgleserver.user.entity.PdfViewingSession;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.PdfViewingSessionRepository;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ViewingSessionService {
    private final PdfViewingSessionRepository pdfViewingSessionRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public void saveSession(Long userId, Long groupId,
                            LocalDateTime enterTime, LocalDateTime exitTime, Long seconds) {
        User user = userRepository.findById(userId).orElseThrow();
        Group group = groupRepository.findById(groupId).orElseThrow();

        PdfViewingSession session = PdfViewingSession.builder()
                .user(user)
                .group(group)
                .enterTime(enterTime)
                .exitTime(exitTime)
                .durationSeconds(seconds)
                .build();

        pdfViewingSessionRepository.save(session);

        // 누적(초) 업데이트
        long add = (seconds == null ? 0L : seconds);
        user.setTotalActiveSeconds((user.getTotalActiveSeconds() == null ? 0L : user.getTotalActiveSeconds()) + add);
        // total_active_hours 는 DB 생성 컬럼이므로 자동 반영
    }

    @Transactional(readOnly = true)
    public long getTotalViewingSeconds(Long userId) {
        return pdfViewingSessionRepository.sumTotalViewingTimeByUserId(userId);
    }

    // 시 단위(내림)
    @Transactional(readOnly = true)
    public int getTotalActiveHoursFloor(Long userId) {
        long totalSec = getTotalViewingSeconds(userId);
        return (int) (totalSec / 3600);
    }

//
    // "12h 05m" 같은 포맷이 필요하면
    @Transactional(readOnly = true)
    public String getPrettyHhMm(Long userId) {
        long totalSec = getTotalViewingSeconds(userId);
        long hours   = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        return String.format("%dh %02dm", hours, minutes);
    }

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
                .totalActiveHours(user.getTotalActiveHours())      // 또는 user.getTotalActiveHours() (생성 컬럼이면)
                .prettyActiveTime(pretty)
                .totalActiveSeconds(totalSec)
                .completedGroups(completed)
                .incompleteGroups(incomplete)
                .participatedGroups(participated)
                .profileColor(user.getProfileColor())
                .progressPercent(0.0)
                .build();

//        // 합계 초 가져오기
//        long totalSec =getTotalViewingSeconds(userId);
//
//        // 시/분 계산
//        long hours = totalSec / 3600;
//        long minutes = (totalSec % 3600) / 60;
//
//        // 포맷(영문)
//        String pretty;
//        if (hours == 0) {
//            pretty = String.format("%d분", minutes);      // 59분이면 "59분"
//        } else {
//            pretty = String.format("%d시간 %02d분", hours, minutes); // 3시간 05분
//        }
//
//
//
//
//        // 한국어 포맷 원하면 아래 사용:
//        // String pretty = String.format("%d시간 %02d분", hours, minutes);
//
//        int totalActiveHours = (int)(totalSec / 3600);
//        int completed = groupMemberRepository.countCompletedGroupsByUserId(userId);
//        int incomplete = groupMemberRepository.countIncompleteGroupsByUserId(userId);
//        int participated = groupMemberRepository.countJoinedGroupsByUserId(userId);
//
//        return UserProfileResponse.builder()
//                .email(user.getEmail())
//                .nickname(user.getNickname())
//                .profileImgUrl(user.getProfileImageUrl())
//                .avgRating(user.getAvgRating() == null ? 0.0 : user.getAvgRating())
//                .totalActiveHours(totalActiveHours)
//                .completedGroups(completed)
//                .incompleteGroups(incomplete)
//                .participatedGroups(participated)
//                .progressPercent(0.0)
//                .profileColor(user.getProfileColor())
//
//                .prettyActiveTime(pretty)
//                .totalActiveSeconds(totalSec)
//                .build();
    }


}
