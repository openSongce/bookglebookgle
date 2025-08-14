package com.example.bookglebookgleserver.group.entity;

import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.pdf.entity.PdfReadingProgress;
import com.example.bookglebookgleserver.user.entity.User;
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
@Table(name = "`group`") // group은 예약어이므로 백틱 사용
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User hostUser;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pdf_id", nullable = false, unique = true)
    private PdfFile pdfFile;

    private String roomTitle;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private Group.Category category;

    private int minRequiredRating;

    @Column(length = 255)
    private String schedule;

    private float progressPercent;  // 그룹 전체의 현재 진도율

    private int groupMaxNum;

    private int totalPages;

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
        STUDY, REVIEW, READING
    }

    public enum ReadingMode {
        FREE, FOLLOW, GATE
    }

    public void setPdfFile(PdfFile pdfFile) {
        this.pdfFile = pdfFile;
        pdfFile.setGroup(this);
    }

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GroupMember> groupMembers = new ArrayList<>();

    @OneToOne(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private ChatRoom chatRoom;

    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<PdfReadingProgress> readingProgresses = new ArrayList<>();

}
