package com.example.bookglebookgleserver.chat.grpc;

import com.example.bookglebookgleserver.chat.ChatMessage;
import com.bgbg.ai.grpc.AIServiceProto.ChatMessageResponse;// (AI 응답용 proto 메시지 import)
import com.example.bookglebookgleserver.chat.ChatServiceGrpc;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.chat.repository.ChatMessageRepository;
import com.example.bookglebookgleserver.chat.repository.ChatRoomRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final AiServiceClient aiServiceClient;

    // 채팅방별로 클라이언트 목록 관리
    private final ConcurrentHashMap<Long, Set<StreamObserver<ChatMessage>>> roomObservers = new ConcurrentHashMap<>();
    // 토론 활성 상태 관리
    private final ConcurrentHashMap<Long, Boolean> discussionActiveMap = new ConcurrentHashMap<>();

    @Override
    public StreamObserver<ChatMessage> chat(StreamObserver<ChatMessage> responseObserver) {
        return new StreamObserver<>() {
            private Long groupId = null;

            @Override
            public void onNext(ChatMessage message) {
                try {
                    if (groupId == null) {
                        groupId = message.getGroupId();
                        roomObservers.computeIfAbsent(groupId, k -> new CopyOnWriteArraySet<>()).add(responseObserver);
                        log.info("[gRPC-Chat] 그룹 {}에 클라이언트 연결! 현재 접속 수: {}", groupId, roomObservers.get(groupId).size());
                    }

                    String msgType = message.getType();

                    // === 1. 토론 시그널 분기 및 ai_service gRPC 호출 ===
                    if ("DISCUSSION_START".equals(msgType)) {
                        log.info("[gRPC-Chat] 토론 시작 시그널 수신 - groupId={}", groupId);
                        discussionActiveMap.put(groupId, true); // 토론 활성화
                        try {
                            aiServiceClient.initializeDiscussion(groupId);
                        } catch (Exception e) {
                            log.error("[gRPC-Chat] AI 토론 시작 요청 실패", e);
                        }
                        broadcastToRoom(groupId, message);
                    } else if ("DISCUSSION_END".equals(msgType)) {
                        log.info("[gRPC-Chat] 토론 종료 시그널 수신 - groupId={}", groupId);
                        discussionActiveMap.remove(groupId); // 토론 비활성화
                        try {
                            aiServiceClient.endDiscussion(groupId);
                        } catch (Exception e) {
                            log.error("[gRPC-Chat] AI 토론 종료 요청 실패", e);
                        }
                        broadcastToRoom(groupId, message);
                    }
                    // === 2. 토론 중 AI Moderation (피드백/알림) 요청 ===
                    else if (isDiscussionActive(groupId)) {
                        aiServiceClient.streamChatModeration(
                                groupId, message.getSenderId(), message.getSenderName(), message.getContent(),
                                new StreamObserver<ChatMessageResponse>() {
                                    @Override
                                    public void onNext(ChatMessageResponse aiResp) {
                                        ChatMessage aiMsg = ChatMessage.newBuilder()
                                                .setGroupId(groupId)
                                                .setSenderId(0L)
                                                .setSenderName("AI")
                                                .setTimestamp(System.currentTimeMillis())
                                                .setType("AI_RESPONSE")
                                                .setAiResponse(aiResp.getAiResponse())
                                                .addAllSuggestedTopics(aiResp.getSuggestedTopicsList())
                                                .build();
                                        broadcastToRoom(groupId, aiMsg);
                                    }
                                    @Override public void onError(Throwable t) { log.error("AI 응답 오류", t); }
                                    @Override public void onCompleted() { }
                                }
                        );
                        // (옵션) AI Moderation 요청 중에도 일반 메시지는 저장/브로드캐스트할지 결정
                        // 아래 두 줄을 넣으면 토론 중 채팅도 저장/전파됨
                        saveAndBroadcastNormalMessage(message, groupId);
                    }
                    // === 3. 일반 채팅 메시지 처리 ===
                    else {
                        saveAndBroadcastNormalMessage(message, groupId);
                    }

                } catch (Exception e) {
                    log.error("[gRPC-Chat] 처리 중 예외 발생", e);
                }
            }

            private void saveAndBroadcastNormalMessage(ChatMessage message, Long groupId) {
                // 채팅방, 유저 조회 및 예외처리
                ChatRoom chatRoom = chatRoomRepository.findById(groupId).orElse(null);
                if (chatRoom == null) {
                    log.warn("[gRPC-Chat] groupId={} ChatRoom 없음! 메시지 저장/전송 생략: {}", groupId, message.getContent());
                    return;
                }
                User sender = userRepository.findById(message.getSenderId()).orElse(null);
                if (sender == null) {
                    log.warn("[gRPC-Chat] senderId={} User 없음! 메시지 저장/전송 생략", message.getSenderId());
                    return;
                }
                // DB 저장
                try {
                    com.example.bookglebookgleserver.chat.entity.ChatMessage entity =
                            com.example.bookglebookgleserver.chat.entity.ChatMessage.builder()
                                    .chatRoom(chatRoom)
                                    .sender(sender)
                                    .content(message.getContent())
                                    .createdAt(Instant.ofEpochMilli(message.getTimestamp())
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime())
                                    .build();
                    chatMessageRepository.save(entity);
                    log.info("[gRPC-Chat] 메시지 저장: roomId={}, sender={}, content={}", groupId, sender.getId(), message.getContent());
                } catch (Exception ex) {
                    log.error("[gRPC-Chat] 메시지 저장 에러: {}", ex.getMessage(), ex);
                }
                // 브로드캐스트
                broadcastToRoom(groupId, message);
            }

            private void broadcastToRoom(Long groupId, ChatMessage msg) {
                Set<StreamObserver<ChatMessage>> observers = roomObservers.getOrDefault(groupId, Set.of());
                for (StreamObserver<ChatMessage> observer : observers) {
                    try { observer.onNext(msg); }
                    catch (Exception e) {
                        observers.remove(observer);
                        try { observer.onCompleted(); } catch (Exception ex) { }
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("[gRPC-Chat] 클라이언트 채널 오류 발생: {}", t.getMessage(), t);
                removeObserver();
            }

            @Override
            public void onCompleted() {
                log.info("[gRPC-Chat] 클라이언트 채널 정상 종료");
                removeObserver();
                responseObserver.onCompleted();
            }

            private void removeObserver() {
                if (groupId != null) {
                    Set<StreamObserver<ChatMessage>> observers = roomObservers.get(groupId);
                    if (observers != null) {
                        observers.remove(responseObserver);
                        log.info("[gRPC-Chat] 그룹 {}에서 클라이언트 연결 해제! 남은 접속 수: {}", groupId, observers.size());
                    }
                }
            }

            private boolean isDiscussionActive(Long groupId) {
                return discussionActiveMap.getOrDefault(groupId, false);
            }
        };
    }
}
