package com.example.bookglebookgleserver.fcm.service;

import com.example.bookglebookgleserver.chat.repository.ChatRoomMemberRepository;
import com.example.bookglebookgleserver.chat.service.ActiveChatRegistry;
import com.example.bookglebookgleserver.fcm.dto.FcmSendRequest;
import com.example.bookglebookgleserver.fcm.repository.FcmQueryRepository;
import com.example.bookglebookgleserver.user.entity.UserDevice;
import com.example.bookglebookgleserver.user.repository.UserDeviceRepository; // ✅ 추가
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmGroupService {

    private final ActiveChatRegistry activeChatRegistry;
    private final FcmQueryRepository fcmQueryRepository;
    private final FcmService fcmService;
    private final UserDeviceRepository userDeviceRepository; // ✅ 추가
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public void sendGroupNow(Long groupId, String title, String body, String channelId, Map<String, String> data) {
        List<String> tokens = fcmQueryRepository.findFcmTokensByGroupId(groupId);
        if (tokens.isEmpty()) {
            log.info("👥 그룹 발송 생략: groupId={}, 기기 토큰 없음", groupId);
            return;
        }
        log.info("👥 그룹 발송 시작: groupId={}, 대상토큰수={}", groupId, tokens.size());
        FcmSendRequest req = new FcmSendRequest(null, null, title, body, channelId, data, true);
        fcmService.sendToTokens(tokens, req);
    }

    public void sendMeetingStart(Long groupId, String groupName) {
        sendGroupNow(
                groupId,
                groupName,
                "",
                null,
                Map.of("type","MEETING_START","groupId", String.valueOf(groupId))
        );
    }

    @Transactional(readOnly = true)
    public void sendChat(Long groupId, Long senderId, String groupName, String senderName, String chatText) {
        List<Long> allUserIds = chatRoomMemberRepository.findUserIdsByRoomId(groupId);

//        // 1) 그룹 대상 토큰 (중복 제거)
//        List<String> groupTokens = fcmQueryRepository.findFcmTokensByGroupId(groupId);
//        List<String> tokens = new ArrayList<>(new HashSet<>(groupTokens));
//
//        // 2) 발신자 모든 기기 토큰 조회 후 제외
//        List<String> senderTokens = userDeviceRepository.findAllByUser_IdAndEnabledTrue(senderId)
//                .stream().map(UserDevice::getToken).toList();
//        tokens.removeAll(senderTokens);


        // 2) 접속중 & 발신자 제외
        Set<Long> active = activeChatRegistry.getActiveUsers(groupId);
        Set<Long> targets = allUserIds.stream()
                .filter(uid -> !uid.equals(senderId))
                .filter(uid -> !active.contains(uid))
                .collect(Collectors.toSet());

        if (targets.isEmpty()) {
            log.info("👥 채팅 알림 생략: groupId={}, 대상 userId 없음 (sender={}, active={})", groupId, senderId, active.size());
            return;
        }

        // 3) 대상 userId의 활성 토큰 조회
        List<String> tokens = userDeviceRepository.findEnabledTokensByUserIds(targets);
        if (tokens.isEmpty()) {
            log.info("👥 채팅 알림 생략: groupId={}, 대상 토큰 없음", groupId);
            return;
        }

        log.info("👥 채팅 알림: groupId={}, 대상토큰수={}, 접속중제외수={}",
                groupId, tokens.size(), active.size());


        fcmService.sendToTokens(tokens, new FcmSendRequest(
                null, null,
                groupName,                         // title
                senderName + (chatText != null && !chatText.isBlank() ? ": " + chatText : ""),
                null,
                Map.of(
                        "type", "CHAT_NEW_MESSAGE",
                        "groupId", String.valueOf(groupId),
                        "senderId", String.valueOf(senderId),
                        "senderNick", senderName,
                        "preview", chatText == null ? "" : chatText
                ),
                true // dataOnly 권장
        ));



//        fcmService.sendToTokens(tokens, new FcmSendRequest(
//                null, null,
//                groupName,                         // title: 그룹 이름
//                senderName + ": " + chatText,      // body: "발신자: 내용"
//                null,
//                Map.of("type","CHAT","groupId", String.valueOf(groupId)),
//                true                                // dataOnly
//        ));
    }
}
