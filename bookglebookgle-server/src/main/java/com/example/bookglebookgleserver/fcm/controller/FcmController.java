package com.example.bookglebookgleserver.fcm.controller;

import com.example.bookglebookgleserver.fcm.dto.FcmSendRequest;
import com.example.bookglebookgleserver.fcm.dto.FcmTokenRegisterRequest;
import com.example.bookglebookgleserver.fcm.service.FcmGroupService;
import com.example.bookglebookgleserver.fcm.service.FcmService;
import com.example.bookglebookgleserver.fcm.service.GroupNotificationScheduler;
import com.example.bookglebookgleserver.user.entity.UserDevice;
import com.example.bookglebookgleserver.user.repository.UserDeviceRepository;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/fcm")
public class FcmController {

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmService fcmService;
    private final FcmGroupService fcmGroupService;
    private final GroupNotificationScheduler scheduler;

    // (1) Android가 로그인 후 토큰 등록/업데이트
    @PutMapping("/token")
    @Transactional
    public ResponseEntity<Void> registerToken(
            @AuthenticationPrincipal(expression = "id") Long userId,
            @RequestBody FcmTokenRegisterRequest req,
            @RequestParam(required = false) Long uidFallback
    ) {
        Long uid = (userId != null) ? userId : uidFallback;
        if (uid == null || req.token() == null || req.token().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        var user = userRepository.findById(uid).orElse(null);
        if (user == null) return ResponseEntity.badRequest().build();

        var existed = userDeviceRepository.findByUser_IdAndToken(uid, req.token());
        if (existed.isPresent()) {
            var d = existed.get();
            d.setEnabled(true);
            d.setLastSeenAt(LocalDateTime.now());
            userDeviceRepository.save(d);
        } else {
            var d = UserDevice.builder()
                    .user(user) // 영속 엔티티 참조
                    .token(req.token())
                    .enabled(true)
                    .lastSeenAt(LocalDateTime.now())
                    .build();
            userDeviceRepository.save(d);
        }
        return ResponseEntity.ok().build();
    }

    // (2) 단건/유저 전체 발송 테스트
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
    @PostMapping("/group/{groupId}/send")
    public ResponseEntity<Void> sendGroupNow(@PathVariable Long groupId,
                                             @RequestParam(defaultValue = "북글북글 알림") String title,
                                             @RequestParam(defaultValue = "지금 함께 읽어요 📚") String body,
                                             @RequestParam(defaultValue = "default") String channelId) {
        fcmGroupService.sendGroupNow(groupId, title, body, channelId, Map.of("groupId", String.valueOf(groupId)));
        return ResponseEntity.ok().build();
    }

    // (4) 스케줄 등록/해제 (필요 시)
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
