package com.example.bookglebookgleserver.user.service;

import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.entity.GroupMember;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.user.dto.UserProfileResponse;
import com.example.bookglebookgleserver.user.entity.PdfViewingSession;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.PdfViewingSessionRepository;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;



@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PdfViewingSessionRepository pdfViewingSessionRepository;

    public void saveSession(Long userId, Long groupId, LocalDateTime enterTime, LocalDateTime exitTime, Long seconds) {
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
}
