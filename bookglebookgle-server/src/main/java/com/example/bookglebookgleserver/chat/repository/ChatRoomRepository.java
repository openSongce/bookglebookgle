package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByGroupId(Long groupId);

    // 방 목록: 서버에서 정렬 완료(pinned DESC → last_message_at DESC, null은 맨 아래)
    @Query("""
      select r from ChatRoom r
        join ChatRoomMember m on m.chatRoom = r and m.user = :user
      order by r.pinned desc,
               case when r.lastMessageAt is null then 1 else 0 end asc,
               r.lastMessageAt desc
    """)
    List<ChatRoom> findRoomsForUserSorted(@Param("user") User user);
}
