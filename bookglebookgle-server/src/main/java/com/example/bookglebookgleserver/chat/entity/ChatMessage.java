package com.example.bookglebookgleserver.chat.entity;

import com.example.bookglebookgleserver.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "chat_message",
        indexes = {
                @Index(name = "idx_chat_msg_room_id_id", columnList = "room_id, id"),
                @Index(name = "idx_chat_msg_room_id_created_at", columnList = "room_id, created_at")
        }
)
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
