package com.example.bookglebookgleserver.fcm.service;

import com.example.bookglebookgleserver.fcm.dto.FcmSendRequest;
import com.example.bookglebookgleserver.user.entity.UserDevice;
import com.example.bookglebookgleserver.user.repository.UserDeviceRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private static final int CHUNK = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final UserDeviceRepository userDeviceRepository;

    /** 특정 유저의 모든 활성 기기에 발송 */
    public void sendToUser(Long userId, FcmSendRequest req) {
        List<String> tokens = userDeviceRepository.findAllByUser_IdAndEnabledTrue(userId)
                .stream()
                .map(UserDevice::getToken)
                .collect(Collectors.toList());
        log.info("📨 유저 전체 기기 발송 준비: userId={}, 대상토큰수={}", userId, tokens.size());
        sendToTokens(tokens, req);
    }

    /** 특정 토큰 1개로 발송 */
    public String sendToToken(String token, FcmSendRequest req) {
        if (token == null || token.isBlank()) {
            log.info("⚠️ 전송 생략: 토큰이 비어있습니다");
            return null;
        }
        Message msg = buildMessage(token, req);
        try {
            String id = firebaseMessaging.send(msg);
            log.info("✅ 단건 발송 성공: messageId={}, tokenHash={}", id, token.hashCode());
            return id;
        } catch (FirebaseMessagingException e) {
            handleFirebaseError(e, token);
            throw new RuntimeException(e);
        }
    }

    /** 여러 토큰 멀티캐스트 */
    public void sendToTokens(List<String> tokens, FcmSendRequest req) {
        if (tokens == null || tokens.isEmpty()) {
            log.info("⚠️ 전송 생략: 대상 토큰이 없습니다");
            return;
        }
        log.info("📦 멀티캐스트 발송 시작: 총토큰수={}", tokens.size());

        for (int i = 0; i < tokens.size(); i += CHUNK) {
            List<String> slice = tokens.subList(i, Math.min(i + CHUNK, tokens.size()));
            MulticastMessage mm = buildMulticastMessage(slice, req);
            try {
                // ✅ 변경 포인트: 배치 엔드포인트를 사용하지 않는 공식 대체 API
                BatchResponse resp = firebaseMessaging.sendEachForMulticast(mm);
                log.info("✅ 멀티캐스트 발송 결과: 성공={} 실패={}", resp.getSuccessCount(), resp.getFailureCount());
                cleanupInvalidTokens(slice, resp);
            } catch (FirebaseMessagingException e) {
                log.error("❌ 멀티캐스트 발송 오류: {}", e.getMessage(), e);
            }
        }
    }

    private static String get(Map<String, String> m, String k) {
        return (m != null) ? m.get(k) : null;
    }

    private Message buildMessage(String token, FcmSendRequest req) {
        // ✅ 항상 data-only
        AndroidConfig android = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(Duration.ofMinutes(10).toMillis())
                .build();

        Message.Builder mb = Message.builder()
                .setAndroidConfig(android)
                .setToken(token);

        // ---- 표준 데이터 채우기 ----
        // 호출측이 req.data()에 "type","groupId"를 넣어주면 여기서 보정/보호
        String type    = Optional.ofNullable(get(req.data(), "type")).orElse("");       // CHAT | MEETING_START
        String groupId = Optional.ofNullable(get(req.data(), "groupId")).orElse("");    // 예: "group123"

        String title = Optional.ofNullable(req.title()).orElse(""); // 그룹 이름 기대
        String body  = "MEETING_START".equals(type)
                ? "" // 모임 시작은 바디 비움(클라에서 "모임이 시작됐어요!"로 처리)
                : Optional.ofNullable(req.body()).orElse(""); // 채팅: "닉네임: 메시지"

        // 먼저 extra data를 넣고(있으면), 그 위에 표준 키를 덮어써서 일관성 보장
        if (req.data() != null && !req.data().isEmpty()) mb.putAllData(req.data());
        mb.putData("type", type)
                .putData("groupId", groupId)
                .putData("title", title)    // 그룹 이름
                .putData("body", body);     // 채팅 내용(발신자 포함) 또는 빈값

        // 채널을 클라에서 만들 수 있게 data로도 넣어줌(선택)
        if (req.channelId() != null) {
            mb.putData("channelId", req.channelId());
        }

        // 타임스탬프/메시지ID 같은 메타도 실무에서 유용
        mb.putData("createdAt", String.valueOf(System.currentTimeMillis()))
                .putData("messageId", UUID.randomUUID().toString());

        return mb.build();
    }

    private MulticastMessage buildMulticastMessage(List<String> tokens, FcmSendRequest req) {
        // ✅ 항상 data-only
        AndroidConfig android = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(Duration.ofMinutes(10).toMillis())
                .build();

        MulticastMessage.Builder mb = MulticastMessage.builder()
                .setAndroidConfig(android)
                .addAllTokens(tokens);

        String type    = Optional.ofNullable(get(req.data(), "type")).orElse("");
        String groupId = Optional.ofNullable(get(req.data(), "groupId")).orElse("");

        String title = Optional.ofNullable(req.title()).orElse("");
        String body  = "MEETING_START".equals(type) ? "" : Optional.ofNullable(req.body()).orElse("");

        if (req.data() != null && !req.data().isEmpty()) mb.putAllData(req.data());
        mb.putData("type", type)
                .putData("groupId", groupId)
                .putData("title", title)
                .putData("body", body);

        if (req.channelId() != null) {
            mb.putData("channelId", req.channelId());
        }

        mb.putData("createdAt", String.valueOf(System.currentTimeMillis()))
                .putData("messageId", UUID.randomUUID().toString());

        return mb.build();
    }


    /** 실패 토큰 비활성화(UNREGISTERED) */
    private void cleanupInvalidTokens(List<String> tokens, BatchResponse resp) {
        for (int i = 0; i < resp.getResponses().size(); i++) {
            SendResponse r = resp.getResponses().get(i);
            if (!r.isSuccessful()) {
                Exception ex = r.getException();
                if (ex instanceof FirebaseMessagingException fme) {
                    if (fme.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                        String t = tokens.get(i);
                        userDeviceRepository.findByToken(t).ifPresent(d -> {
                            d.setEnabled(false);
                            userDeviceRepository.save(d);
                            log.warn("🧹 무효 토큰 비활성화: userId={}, tokenHash={}", d.getUser().getId(), t.hashCode());
                        });
                    } else {
                        log.warn("⚠️ 개별 전송 실패: code={}, idx={}", fme.getMessagingErrorCode(), i);
                    }
                }
            }
        }
    }

    private void handleFirebaseError(FirebaseMessagingException e, String token) {
        log.warn("❗ FCM 전송 실패: code={}, message={}", e.getMessagingErrorCode(), e.getMessage());
        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
            userDeviceRepository.findByToken(token).ifPresent(d -> {
                d.setEnabled(false);
                userDeviceRepository.save(d);
                log.warn("🧹 무효 토큰 비활성화(단건): userId={}, tokenHash={}", d.getUser().getId(), token.hashCode());
            });
        }
    }
}
