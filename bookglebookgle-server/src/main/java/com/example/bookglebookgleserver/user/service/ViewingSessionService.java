package com.example.bookglebookgleserver.user.service;


import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.user.entity.PdfViewingSession;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.PdfViewingSessionRepository;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ViewingSessionService {
    private final PdfViewingSessionRepository pdfViewingSessionRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

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

    // 시 단위(반올림/올림이 필요하면 아래 중 택1)
    @Transactional(readOnly = true)
    public int getTotalActiveHoursRound(Long userId) {
        long totalSec = getTotalViewingSeconds(userId);
        return (int) Math.round(totalSec / 3600.0);
    }

    @Transactional(readOnly = true)
    public int getTotalActiveHoursCeil(Long userId) {
        long totalSec = getTotalViewingSeconds(userId);
        return (int) Math.ceil(totalSec / 3600.0);
    }

    // "12h 05m" 같은 포맷이 필요하면
    @Transactional(readOnly = true)
    public String getPrettyHhMm(Long userId) {
        long totalSec = getTotalViewingSeconds(userId);
        long hours   = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        return String.format("%dh %02dm", hours, minutes);
    }

}
