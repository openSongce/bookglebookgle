package com.example.bookglebookgleserver.chat.entity;

import com.example.bookglebookgleserver.group.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
@Table(name = "chat_room",
        indexes = {
                @Index(name = "ix_room_last_msg_at", columnList = "pinned,last_message_at")
        }
)
public class ChatRoom {

    @Id
    @Column(name = "group_id")
    private Long groupId;  // Group PK를 그대로 사용

    @OneToOne
    @MapsId
    @JoinColumn(name = "group_id")
    private Group group;

    private String groupTitle;
    private String imageUrl;
    private String category;

    // === 정렬/표시용 필드 ===
    @Column(name = "last_message_id")
    private Long lastMessageId; // 마지막 NORMAL 메시지 PK

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt; // 정렬 기준

    @Column(name = "last_message", length = 300)
    private String lastMessage; // 미리보기(선택)

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false; // 상단 고정

    private int memberCount; // 유지할 계획이면 그대로 사용, 아니면 조회 쿼리에서 COUNT로 대체 권장

    @Builder.Default
    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatRoomMember> chatRoomMembers = new ArrayList<>();
}
