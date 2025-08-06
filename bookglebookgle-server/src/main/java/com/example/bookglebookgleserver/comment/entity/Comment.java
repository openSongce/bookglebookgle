package com.example.bookglebookgleserver.comment.entity;

import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_file_id")
    private PdfFile pdfFile;

    private Long groupId;
    private Long userId;
    private int page;
    private String snippet; // 주석 대상 텍스트
    private String text;    // 실제 댓글 내용
    private double startX, startY, endX, endY;

    // ⭐️ 생성일 추가
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
