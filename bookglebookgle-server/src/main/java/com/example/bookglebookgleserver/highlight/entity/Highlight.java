package com.example.bookglebookgleserver.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "highlight")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Highlight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;
    private Long userId;
    private int page;
    private String snippet; // 하이라이트 텍스트
    private String color;
    private double startX, startY, endX, endY;

    // 생성일, 수정일 등 필요시 추가
}

