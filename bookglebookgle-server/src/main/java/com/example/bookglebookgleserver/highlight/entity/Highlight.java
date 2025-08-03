package com.example.bookglebookgleserver.highlight.entity;

import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import com.example.bookglebookgleserver.user.entity.User;
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
    @Column(name = "highlight_id")
    private Long highlightId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_id", nullable = false)
    private PdfFile pdfFile;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "rect_x")
    private Float rectX;

    @Column(name = "rect_y")
    private Float rectY;

    @Column(name = "rect_w")
    private Float rectW;

    @Column(name = "rect_h")
    private Float rectH;

    @Column(length = 20)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
