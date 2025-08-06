package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 최신 메시지 30개 (beforeId 없이)
    List<ChatMessage> findTop30ByRoomIdOrderByIdDesc(Long roomId);

    // beforeId보다 작은 메시지 30개
    List<ChatMessage> findTop30ByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeId);

    // (아래는 필요하면 유지, 실제 사용 안 하면 삭제해도 됨)
    // 전체 메시지 개수
    int countByRoomId(Long roomId);

    // 안 읽은 메시지 개수
    int countByRoomIdAndIdGreaterThan(Long roomId, Long messageId);

    // 최신 메시지 한 개
    ChatMessage findFirstByRoomIdOrderByCreatedAtDesc(Long roomId);
}
