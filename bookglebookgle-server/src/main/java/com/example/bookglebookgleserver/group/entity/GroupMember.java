package com.example.bookglebookgleserver.group.entity;

import com.example.bookglebookgleserver.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"group_id", "user_id"}) // 중복 참여 방지
        }
)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private int lastPageRead;

    private float progressPercent;

    private boolean isFollowingHost;

    private LocalDateTime joinedAt;

    private boolean isHost;
    @PrePersist
    public void prePersist() {
        this.joinedAt = LocalDateTime.now();
    }
}
