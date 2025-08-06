package com.example.bookglebookgleserver.pdf.entity;

import com.example.bookglebookgleserver.comment.entity.Comment;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.highlight.entity.Highlight;
import com.example.bookglebookgleserver.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pdf_id")
    private Long pdfId;

    private String fileName;

    private int pageCnt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // ← 이게 중요
    private User uploadUser;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @OneToOne(mappedBy = "pdfFile")
    private Group group;

    public void setGroup(Group group) {
        this.group = group;
    }

    @OneToMany(mappedBy = "pdfFile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Highlight> highlights = new ArrayList<>();

    @OneToMany(mappedBy = "pdfFile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

}
