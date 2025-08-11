package com.example.bookglebookgleserver.fcm.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.fcm.dto.FcmSendRequest;
import com.example.bookglebookgleserver.fcm.dto.FcmTokenRegisterRequest;
import com.example.bookglebookgleserver.fcm.service.FcmGroupService;
import com.example.bookglebookgleserver.fcm.service.FcmService;
import com.example.bookglebookgleserver.fcm.service.GroupNotificationScheduler;
import com.example.bookglebookgleserver.user.entity.UserDevice;
import com.example.bookglebookgleserver.user.repository.UserDeviceRepository;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/fcm")
public class FcmController {

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmService fcmService;
    private final FcmGroupService fcmGroupService;
    private final GroupNotificationScheduler scheduler;

    // (1) 로그인/앱 시작/onNewToken 이후: 토큰 등록/업데이트 + 중복 토큰 정리
    @Operation(
            summary = "FCM 토큰 등록/갱신",
            description = """
        로그인/앱 시작/onNewToken 이후 호출.
        - 동일 토큰이 다른 유저에 묶여있으면 해당 레코드 모두 비활성화.
        - (userId, token) 조합이 없으면 신규 생성, 있으면 enabled=true로 갱신.
        """
    )
    @PutMapping("/token")
    @Transactional
    public ResponseEntity<Void> registerToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody FcmTokenRegisterRequest req,
            @RequestParam(required = false) Long uidFallback // 테스트용
    ) {
        Long uid = (userDetails != null && userDetails.getUser() != null)
                ? userDetails.getUser().getId()
                : uidFallback;

        if (uid == null || req.token() == null || req.token().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        var user = userRepository.findById(uid).orElse(null);
        if (user == null) return ResponseEntity.badRequest().build();

        String token = req.token();

        // ✅ 같은 토큰이 다른 유저에 묶여 있던 레코드 모두 비활성화(중복/계정전환 정리)
        var duplicates = userDeviceRepository.findAllByToken(token);
        for (var d : duplicates) {
            if (!Objects.equals(d.getUser().getId(), uid) && d.isEnabled()) {
                d.setEnabled(false);
                userDeviceRepository.save(d);
            }
        }

        // ✅ (userId, token) 업서트
        var existed = userDeviceRepository.findByUser_IdAndToken(uid, token);
        if (existed.isPresent()) {
            var d = existed.get();
            d.setEnabled(true);
            d.setLastSeenAt(LocalDateTime.now());
            userDeviceRepository.save(d);
        } else {
            var d = UserDevice.builder()
                    .user(user)
                    .token(token)
                    .enabled(true)
                    .lastSeenAt(LocalDateTime.now())
                    .build();
            userDeviceRepository.save(d);
        }
        return ResponseEntity.ok().build();
    }

    // (1-1) 로그아웃: 해당 토큰만 비활성화
    @Operation(
            summary = "FCM 토큰 비활성화(로그아웃)",
            description = """
        로그아웃 시 호출.
        - 해당 (userId, token)만 enabled=false로 비활성화.
        - 이후 이 기기는 푸시 발송 대상에서 제외됨.
        """
    )
    @PostMapping("/unregister")
    @Transactional
    public ResponseEntity<Void> unregisterToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody FcmTokenRegisterRequest req,
            @RequestParam(required = false) Long uidFallback // 테스트용
    ) {
        Long uid = (userDetails != null && userDetails.getUser() != null)
                ? userDetails.getUser().getId()
                : uidFallback;

        if (uid == null || req.token() == null || req.token().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        userDeviceRepository.findByUser_IdAndToken(uid, req.token()).ifPresent(d -> {
            d.setEnabled(false);
            d.setLastSeenAt(LocalDateTime.now());
            userDeviceRepository.save(d);
        });
        return ResponseEntity.ok().build();
    }

    // (2) 단건/유저 전체 발송 테스트
    @Operation(
            summary = "단일 토큰/유저 전체 발송 테스트",
            description = "토큰 또는 유저 ID를 지정하여 FCM 발송 테스트"
    )
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> send(@RequestBody FcmSendRequest req) {
        if (req.token() != null && !req.token().isBlank()) {
            String messageId = fcmService.sendToToken(req.token(), req);
            return ResponseEntity.ok(Map.of("messageId", messageId == null ? "" : messageId));
        }
        if (req.userId() != null) {
            fcmService.sendToUser(req.userId(), req);
            return ResponseEntity.ok(Map.of("status", "OK"));
        }
        return ResponseEntity.badRequest().build();
    }

    // (3) 그룹 즉시 발송
    @Operation(
            summary = "그룹 즉시 발송",
            description = "그룹 ID로 FCM을 즉시 발송"
    )
    @PostMapping("/group/{groupId}/send")
    public ResponseEntity<Void> sendGroupNow(@PathVariable Long groupId,
                                             @RequestParam(defaultValue = "북글북글 알림") String title,
                                             @RequestParam(defaultValue = "지금 함께 읽어요 📚") String body,
                                             @RequestParam(defaultValue = "default") String channelId) {
        fcmGroupService.sendGroupNow(groupId, title, body, channelId, Map.of("groupId", String.valueOf(groupId)));
        return ResponseEntity.ok().build();
    }

    // (4) 스케줄 등록/해제
    @PostMapping("/group/{groupId}/schedule")
    public ResponseEntity<Void> registerSchedule(@PathVariable Long groupId, @RequestParam String cron) {
        scheduler.register(groupId, cron);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/group/{groupId}/schedule")
    public ResponseEntity<Void> unregisterSchedule(@PathVariable Long groupId) {
        scheduler.unregister(groupId);
        return ResponseEntity.ok().build();
    }
}
