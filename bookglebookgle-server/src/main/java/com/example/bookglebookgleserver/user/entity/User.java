package com.example.bookglebookgleserver.user.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="user_id")
    private Long id;

    @Column(name="user_email", unique = true, nullable = false)
    private String email;

    private String password;

    @Column(name = "nickname", nullable = false, unique = true)
    private String nickname;

    private String provider;

    @Column(name = "profile_color", length = 9)
    private String profileColor;   // "#RRGGBB" 또는 "#AARRGGBB"

    private String profileImageUrl;

    @Column(name = "avg_rating")
    private Float avgRating;

    @Column(name = "rating_cnt")
    private Integer ratingCnt;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "total_active_seconds", nullable = false)
    private Long totalActiveSeconds = 0L;

    // 생성 컬럼 매핑 (DB가 계산하므로 읽기 전용)
    @Column(name = "total_active_hours", insertable = false, updatable = false)
    private Integer totalActiveHours;

    // 편의 getter (분/시 포맷이 필요하면 DTO에서 사용)
    public int getTotalActiveHoursFloor() {
        long sec = (totalActiveSeconds == null ? 0L : totalActiveSeconds);
        return (int) (sec / 3600);
    }

    @PrePersist
    public void prePersist() {
        if (avgRating == null) {
            avgRating = 3.0f;
        }
        if (ratingCnt == null) {
            ratingCnt = 0;
        }
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

}
