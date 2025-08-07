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
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    private Long groupId;  // PK

    @OneToOne
    @MapsId
    @JoinColumn(name = "groupId")
    private Group group;  // Group 엔티티와 1:1 매핑

    private String groupTitle;
    private String imageUrl;
    private String category;

    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private int memberCount;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatRoomMember> chatRoomMembers = new ArrayList<>();
}


