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
@Table(name = "chat_room_member",
        indexes = {
                @Index(name = "ix_member_user_room", columnList = "user_id,group_id")
        }
)
public class ChatRoomMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ChatRoom의 PK 컬럼명과 반드시 일치시킬 것
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Long lastReadMessageId;

    private LocalDateTime joinedAt;

    @PrePersist
    public void onCreate() {
        if (joinedAt == null) joinedAt = LocalDateTime.now();
    }
}
