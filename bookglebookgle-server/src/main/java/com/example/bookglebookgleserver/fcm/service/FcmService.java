package com.example.bookglebookgleserver.fcm.service;

import com.example.bookglebookgleserver.fcm.dto.FcmSendRequest;
import com.example.bookglebookgleserver.user.entity.UserDevice;
import com.example.bookglebookgleserver.user.repository.UserDeviceRepository;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

    private Message buildMessage(String token, FcmSendRequest req) {
        boolean dataOnly = Boolean.TRUE.equals(req.dataOnly());

        AndroidConfig.Builder ab = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(Duration.ofMinutes(10).toMillis());

        if (!dataOnly && req.channelId() != null) {
            ab.setNotification(AndroidNotification.builder()
                    .setTitle(req.title())
                    .setBody(req.body())
                    .setChannelId(req.channelId())
                    .build());
        }

        Message.Builder mb = Message.builder()
                .setAndroidConfig(ab.build())
                .setToken(token);

        if (dataOnly) {
            // 🔹 제목/본문도 data로 내려보내기(클라이언트에서 통일 처리)
            mb.putData("title", Optional.ofNullable(req.title()).orElse(""))
                    .putData("body", Optional.ofNullable(req.body()).orElse(""));
            if (req.data() != null) mb.putAllData(req.data());
        } else {
            // 🔹 혼합(기존): 시스템 표시 + data 부가
            mb.setNotification(Notification.builder()
                    .setTitle(req.title())
                    .setBody(req.body())
                    .build());
            if (req.data() != null) mb.putAllData(req.data());
        }
        return mb.build();
    }

    private MulticastMessage buildMulticastMessage(List<String> tokens, FcmSendRequest req) {
        boolean dataOnly = Boolean.TRUE.equals(req.dataOnly());

        AndroidConfig.Builder ab = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(Duration.ofMinutes(10).toMillis());

        if (!dataOnly && req.channelId() != null) {
            ab.setNotification(AndroidNotification.builder()
                    .setTitle(req.title())
                    .setBody(req.body())
                    .setChannelId(req.channelId())
                    .build());
        }

        MulticastMessage.Builder mb = MulticastMessage.builder()
                .setAndroidConfig(ab.build())
                .addAllTokens(tokens);

        if (dataOnly) {
            mb.putData("title", Optional.ofNullable(req.title()).orElse(""))
                    .putData("body",  Optional.ofNullable(req.body()).orElse(""));
            if (req.data() != null && !req.data().isEmpty()) mb.putAllData(req.data());
        } else {
            mb.setNotification(Notification.builder()
                    .setTitle(req.title())
                    .setBody(req.body())
                    .build());
            if (req.data() != null && !req.data().isEmpty()) mb.putAllData(req.data());
        }

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
