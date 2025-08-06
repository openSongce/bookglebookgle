package com.example.bookglebookgleserver.comment.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;
    private Long userId;
    private int page;
    private String snippet; // 주석 대상 텍스트
    private String text;    // 실제 댓글 내용
    private double startX, startY, endX, endY;

    // 생성일, 수정일 등 필요시 추가
}
