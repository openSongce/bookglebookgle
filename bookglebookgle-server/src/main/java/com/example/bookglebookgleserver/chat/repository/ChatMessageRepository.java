package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByChatRoomOrderByIdDesc(ChatRoom chatRoom, Pageable pageable);

    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByChatRoomAndIdLessThanOrderByIdDesc(ChatRoom chatRoom, Long id, Pageable pageable);



    // 최신 메시지 한 개
    @EntityGraph(attributePaths = "sender")
    ChatMessage findFirstByChatRoomOrderByCreatedAtDesc(ChatRoom chatRoom);

    // 전체 메시지 개수
    int countByChatRoom(ChatRoom chatRoom);

    // 특정 메시지 ID보다 큰(안 읽은) 메시지 개수
    int countByChatRoomAndIdGreaterThan(ChatRoom chatRoom, Long messageId);
}

