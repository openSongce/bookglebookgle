package com.example.bookglebookgleserver.chat.grpc;

import com.example.bookglebookgleserver.chat.ChatMessage; // gRPC(proto) 메시지
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

    // 채팅방별로 클라이언트 목록 관리 (gRPC ChatMessage 기준)
    private final ConcurrentHashMap<Long, Set<StreamObserver<ChatMessage>>> roomObservers = new ConcurrentHashMap<>();

    @Override
    public StreamObserver<ChatMessage> chat(StreamObserver<ChatMessage> responseObserver) {
        return new StreamObserver<ChatMessage>() {
            private Long groupId = null;

            @Override
            public void onNext(ChatMessage message) {
                if (groupId == null) {
                    groupId = message.getGroupId();
                    roomObservers.computeIfAbsent(groupId, k -> new CopyOnWriteArraySet<>()).add(responseObserver);
                    log.info("[gRPC-Chat] 그룹 {}에 클라이언트 연결!", groupId);
                }

                // 채팅방/유저 조회 및 예외처리
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

                // 💡 DB 저장 시에는 JPA 엔티티 패키지명을 명확히!
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

                // 모든 Observer에게 브로드캐스트
                Set<StreamObserver<ChatMessage>> observers = roomObservers.getOrDefault(groupId, Set.of());
                for (StreamObserver<ChatMessage> observer : observers) {
                    try {
                        observer.onNext(message);
                    } catch (Exception e) {
                        log.warn("[gRPC-Chat] Observer 메시지 전송 중 예외: {}", e.getMessage(), e);
                        observers.remove(observer);
                        observer.onCompleted();
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
                    }
                }
            }
        };
    }
}
