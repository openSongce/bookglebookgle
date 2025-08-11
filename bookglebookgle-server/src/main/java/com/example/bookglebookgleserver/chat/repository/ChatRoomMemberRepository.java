package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.chat.entity.ChatRoomMember;
import com.example.bookglebookgleserver.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    Optional<ChatRoomMember> findByChatRoomAndUser(ChatRoom chatRoom, User user);

    List<ChatRoomMember> findByUser(User user);

    int countByChatRoom(ChatRoom chatRoom);

    Optional<ChatRoomMember> findByUserAndChatRoom(User user, ChatRoom room);

    @Query("""
      select m from ChatRoomMember m
      where m.user = :user and m.chatRoom.groupId = :roomId
    """)
    Optional<ChatRoomMember> findByUserAndRoomId(@Param("user") User user,
                                                 @Param("roomId") Long roomId);
}
