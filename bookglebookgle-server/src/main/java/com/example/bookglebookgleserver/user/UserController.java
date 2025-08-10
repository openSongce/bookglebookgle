package com.example.bookglebookgleserver.user;



import com.example.bookglebookgleserver.auth.service.AuthService;
import com.example.bookglebookgleserver.group.repository.GroupMemberRepository;
import com.example.bookglebookgleserver.user.dto.UserProfileResponse;
import com.example.bookglebookgleserver.user.dto.UserProfileUpdateRequest;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.PdfViewingSessionRepository;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import com.example.bookglebookgleserver.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final GroupMemberRepository groupMemberRepository;
    private final PdfViewingSessionRepository pdfViewingSessionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthService authService;

    @Operation(summary = "사용자 프로필 조회", description = "이메일, 닉네임, 프로필 사진, 평점, 참여/완료/미완료 모임 수, 총 활동 시간 반환")
    @GetMapping("/profile")
    public UserProfileResponse getProfile(
            @AuthenticationPrincipal(expression = "username") String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        Long userId = currentUser.getId();

        double avgRating = currentUser.getAvgRating() != null ? currentUser.getAvgRating() : 0.0;
        int participated = groupMemberRepository.countJoinedGroupsByUserId(userId);
        int completed    = groupMemberRepository.countCompletedGroupsByUserId(userId);
        int incomplete   = groupMemberRepository.countIncompleteGroupsByUserId(userId);
        Long totalSeconds = pdfViewingSessionRepository.sumTotalViewingTimeByUserId(userId);
        int totalHours = totalSeconds == null ? 0 : (int)(totalSeconds / 3600);

        return UserProfileResponse.builder()
                .email(currentUser.getEmail())
                .nickname(currentUser.getNickname())
                .profileImgUrl(currentUser.getProfileImageUrl())
                .avgRating(avgRating)
                .participatedGroups(participated)
                .completedGroups(completed)
                .incompleteGroups(incomplete)
                .totalActiveHours(totalHours)
                .profileColor(currentUser.getProfileColor())
                .build();
    }


    @Operation(summary = "프로필 수정", description = "닉네임, 프로필 이미지 URL을 수정합니다.")
    @PutMapping("/profile")
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal(expression = "username") String email,
            @RequestBody UserProfileUpdateRequest request) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));



        // 닉네임 변경 요청이 있고, 현재 닉네임과 다를 경우 중복 체크
        if (request.getNickname() != null
                && !request.getNickname().isBlank()
                && !request.getNickname().equals(currentUser.getNickname())) {

            boolean exists = authService.isNicknameDuplicated(request.getNickname());
            if (exists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
            }
            currentUser.setNickname(request.getNickname());
        }

        if (request.getProfileImgUrl() != null && !request.getProfileImgUrl().isBlank()) {
            currentUser.setProfileImageUrl(request.getProfileImgUrl());
        }

        if (request.getProfileColor() != null && !request.getProfileColor().isBlank()) {
            currentUser.setProfileColor(request.getProfileColor().trim().toUpperCase());
        }

        userRepository.save(currentUser);

        return UserProfileResponse.builder()
                .email(currentUser.getEmail())
                .nickname(currentUser.getNickname())
                .profileImgUrl(currentUser.getProfileImageUrl())
                .profileColor(currentUser.getProfileColor())
                .build();
    }


}
