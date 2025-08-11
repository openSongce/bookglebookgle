package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 최신 N개 (room 기준)
    List<ChatMessage> findByChatRoomOrderByIdDesc(ChatRoom chatRoom, Pageable pageable);

    // 커서: beforeId보다 작은 것들 중 최신 N개
    List<ChatMessage> findByChatRoomAndIdLessThanOrderByIdDesc(ChatRoom chatRoom, Long id, Pageable pageable);

    // 최신 1개 (createdAt 기준) — 필요 없으면 제거 가능
    ChatMessage findFirstByChatRoomOrderByCreatedAtDesc(ChatRoom chatRoom);

    int countByChatRoom(ChatRoom chatRoom);

    // 특정 ID보다 큰 개수(타입 구분 없음) — NORMAL만 필요하면 서비스에서 쓰지 말고 아래 countUnreadAfter 사용
    int countByChatRoomAndIdGreaterThan(ChatRoom chatRoom, Long messageId);

    // 최신순 커서 페이지네이션 (roomId로 조회)
    @Query("""
      select cm from ChatMessage cm
      where cm.chatRoom.groupId = :roomId
        and (:beforeId is null or cm.id < :beforeId)
      order by cm.id desc
      """)
    List<ChatMessage> findByRoomIdWithCursor(@Param("roomId") Long roomId,
                                             @Param("beforeId") Long beforeId,
                                             Pageable pageable);

    // 가장 최근 NORMAL 1건
    Optional<ChatMessage> findTopByChatRoom_GroupIdAndTypeOrderByIdDesc(
            Long roomId,
            ChatMessage.Type type
    );

    // 멤버의 lastReadMessageId 이후 NORMAL 개수(안읽은 수)
    @Query("""
      select count(cm) from ChatMessage cm
      where cm.chatRoom.groupId = :roomId
        and cm.type = :type
        and cm.id > :lastReadId
      """)
    long countUnreadAfter(@Param("roomId") Long roomId,
                          @Param("lastReadId") Long lastReadId,
                          @Param("type") ChatMessage.Type type);
}
