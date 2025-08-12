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

    public LocalDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(LocalDateTime deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public void setAvgRating(Float avgRating) {
        this.avgRating = avgRating;
    }
}
