package com.example.bookglebookgleserver.highlight.entity;

import com.example.bookglebookgleserver.group.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "highlight")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Highlight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private int page;
    private String snippet; // 하이라이트 텍스트
    private String color;
    private double startX, startY, endX, endY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
