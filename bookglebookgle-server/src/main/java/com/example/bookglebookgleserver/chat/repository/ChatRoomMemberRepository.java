package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatRoomMember;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    Optional<ChatRoomMember> findByChatRoomAndUser(ChatRoom chatRoom, User user);

    List<ChatRoomMember> findByUser(User user);

    int countByChatRoom(ChatRoom chatRoom);

    //방에 속한 모든 멤버의 userId만 빠르게 조회
    @Query("select m.user.id from ChatRoomMember m where m.chatRoom.id = :roomId")
    List<Long> findUserIdsByRoomId(@Param("roomId") Long roomId);
}
