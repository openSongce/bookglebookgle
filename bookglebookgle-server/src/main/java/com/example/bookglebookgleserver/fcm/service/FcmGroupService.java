package com.example.bookglebookgleserver.fcm.service;

import com.example.bookglebookgleserver.fcm.dto.FcmSendRequest;
import com.example.bookglebookgleserver.fcm.repository.FcmQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmGroupService {

    private final FcmQueryRepository fcmQueryRepository;
    private final FcmService fcmService;

    public void sendGroupNow(Long groupId, String title, String body, String channelId, Map<String, String> data) {
        List<String> tokens = fcmQueryRepository.findFcmTokensByGroupId(groupId);
        if (tokens.isEmpty()) {
            log.info("👥 그룹 발송 생략: groupId={}, 기기 토큰 없음", groupId);
            return;
        }
        log.info("👥 그룹 발송 시작: groupId={}, 대상토큰수={}", groupId, tokens.size());
        FcmSendRequest req = new FcmSendRequest(null, null, title, body, channelId, data);
        fcmService.sendToTokens(tokens, req);
    }
}
