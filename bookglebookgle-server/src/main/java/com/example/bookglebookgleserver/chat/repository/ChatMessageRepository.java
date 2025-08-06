package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 채팅방의 메시지 전체 조회(최신순)
    List<ChatMessage> findByChatRoomOrderByCreatedAtDesc(ChatRoom chatRoom);

    // 채팅방 전체 메시지 개수
    int countByChatRoom(ChatRoom chatRoom);

    // 특정 메시지 ID보다 큰(안 읽은) 메시지 개수
    int countByChatRoomAndIdGreaterThan(ChatRoom chatRoom, Long messageId);

    // 채팅방 내 최신 메시지
    ChatMessage findFirstByChatRoomOrderByCreatedAtDesc(ChatRoom chatRoom);

    List<ChatMessage> findByChatRoomAndIdLessThanOrderByIdDesc(ChatRoom chatRoom, Long id, Pageable pageable);

    List<ChatMessage> findByChatRoomOrderByIdDesc(ChatRoom chatRoom, Pageable pageable);
}
