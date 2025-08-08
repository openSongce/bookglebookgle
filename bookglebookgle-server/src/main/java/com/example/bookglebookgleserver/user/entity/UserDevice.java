package com.example.bookglebookgleserver.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_device",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "token"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UserDevice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 2048)
    private String token;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    private LocalDateTime lastSeenAt;

    @PrePersist
    public void prePersist() {
        if (lastSeenAt == null) lastSeenAt = LocalDateTime.now();
    }
}
