package com.example.bookglebookgleserver.chat.entity;

import com.example.bookglebookgleserver.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
@Table(name = "chat_message",
        indexes = {
                @Index(name = "ix_msg_room_type_id", columnList = "room_id,type,id"),
                @Index(name = "ix_msg_room_id", columnList = "room_id")
        }
)
public class ChatMessage {

    public enum Type { NORMAL, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id") // ← 현재 테이블 컬럼명이 group_id라면 여기를 "group_id" 로 맞추세요.
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type = Type.NORMAL;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (type == null) type = Type.NORMAL;
    }
}
