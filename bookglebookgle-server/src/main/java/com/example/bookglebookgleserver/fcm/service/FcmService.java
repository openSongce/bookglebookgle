package com.example.bookglebookgleserver.fcm.service;

import com.example.bookglebookgleserver.fcm.dto.FcmSendRequest;
import com.example.bookglebookgleserver.user.entity.UserDevice;
import com.example.bookglebookgleserver.user.repository.UserDeviceRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
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
                .stream().map(UserDevice::getToken).collect(Collectors.toList());
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
                BatchResponse resp = firebaseMessaging.sendMulticast(mm);
                log.info("✅ 멀티캐스트 발송 결과: 성공={} 실패={}", resp.getSuccessCount(), resp.getFailureCount());
                cleanupInvalidTokens(slice, resp);
            } catch (FirebaseMessagingException e) {
                log.error("❌ 멀티캐스트 발송 오류: {}", e.getMessage(), e);
            }
        }
    }

    // ---- Android 전용 메시지 구성 ----
    private Message buildMessage(String token, FcmSendRequest req) {
        AndroidNotification.Builder nb = AndroidNotification.builder()
                .setTitle(req.title())
                .setBody(req.body());
        if (req.channelId() != null) nb.setChannelId(req.channelId());

        AndroidConfig android = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(Duration.ofMinutes(10).toMillis())
                .setNotification(nb.build())
                .build();

        Message.Builder mb = Message.builder()
                .setAndroidConfig(android)
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(req.title())
                        .setBody(req.body())
                        .build());

        if (req.data() != null && !req.data().isEmpty()) mb.putAllData(req.data());
        return mb.build();
    }

    private MulticastMessage buildMulticastMessage(List<String> tokens, FcmSendRequest req) {
        AndroidNotification.Builder nb = AndroidNotification.builder()
                .setTitle(req.title())
                .setBody(req.body());
        if (req.channelId() != null) nb.setChannelId(req.channelId());

        AndroidConfig android = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(Duration.ofMinutes(10).toMillis())
                .setNotification(nb.build())
                .build();

        MulticastMessage.Builder mb = MulticastMessage.builder()
                .setAndroidConfig(android)
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(req.title())
                        .setBody(req.body())
                        .build());

        if (req.data() != null && !req.data().isEmpty()) mb.putAllData(req.data());
        return mb.build();
    }

    /** 실패 토큰 비활성화(UNREGISTERED) */
    private void cleanupInvalidTokens(List<String> tokens, BatchResponse resp) {
        for (int i = 0; i < resp.getResponses().size(); i++) {
            SendResponse r = resp.getResponses().get(i);
            if (!r.isSuccessful()) {
                Exception ex = r.getException();
                if (ex instanceof FirebaseMessagingException) {
                    FirebaseMessagingException fme = (FirebaseMessagingException) ex;
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
