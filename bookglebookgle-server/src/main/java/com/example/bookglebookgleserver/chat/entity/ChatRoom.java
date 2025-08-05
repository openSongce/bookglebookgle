package com.example.bookglebookgleserver.chat.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {
    @Id
    private Long groupId;           // groupId와 1:1 관계

    private String groupTitle;
    private String imageUrl;
    private String category;

    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private int memberCount;

    // unreadCount는 DB에 저장 안함! → 쿼리로 계산해서 응답에 포함
}

