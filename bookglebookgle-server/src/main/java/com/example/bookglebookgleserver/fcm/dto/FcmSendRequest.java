package com.example.bookglebookgleserver.fcm.dto;

import java.util.Map;

public record FcmSendRequest(
        Long userId,   // userId 지정 시 그 유저의 모든 Android 기기로 발송
        String token,  // 또는 특정 토큰 1개로 발송
        String title,
        String body,
        String channelId,
        Map<String, String> data
) {}
