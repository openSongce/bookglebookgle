package com.example.bookglebookgleserver.fcm.dto;

import java.util.Map;

public record FcmSendRequest(
        Long userId,
        String token,
        String title,
        String body,
        String channelId,
        Map<String, String> data,
        Boolean dataOnly
) {}
