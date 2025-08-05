package com.example.bookglebookgleserver.chat.repository;

import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
}
