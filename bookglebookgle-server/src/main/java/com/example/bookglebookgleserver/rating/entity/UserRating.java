package com.example.bookglebookgleserver.rating.entity;


import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_rating")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserRating {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 그룹 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "g_id")
    private Group group;

    // 평가자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_id")
    private User rater;

    // 평가 대상자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ratee_id")
    private User ratee;

    // 평점
    private Float rating;

    // 작성일시
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }


}
