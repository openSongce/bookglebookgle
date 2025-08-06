package com.example.bookglebookgleserver.ocr.entity;

import com.example.bookglebookgleserver.pdf.entity.PdfFile;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ocr_result")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ocr_id")
    private Long ocrId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_id", nullable = false)
    private PdfFile pdfFile;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "word_index")
    private Integer wordIndex;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "rect_x")
    private Float rectX;

    @Column(name = "rect_y")
    private Float rectY;

    @Column(name = "rect_w")
    private Float rectW;

    @Column(name = "rect_h")
    private Float rectH;
}
