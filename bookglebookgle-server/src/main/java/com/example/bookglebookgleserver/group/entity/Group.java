package com.example.bookglebookgleserver.group.entity;

import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "`group`") // group은 예약어이므로 백틱 사용
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User hostUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_id", nullable = false)
    private PdfFile pdfFile;

    private String roomTitle;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private Group.Category category;

    private int minRequiredRating;

    private LocalDateTime schedule;

    private float progressPercent;  // 그룹 전체의 현재 진도율

    private int groupMaxNum;

    @Enumerated(EnumType.STRING)
    private ReadingMode readingMode;

    private boolean isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isDeleted = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum Category {
        STUDY, DISCUSSION, READING
    }

    public enum ReadingMode {
        FREE, FOLLOW, GATE
    }
}
