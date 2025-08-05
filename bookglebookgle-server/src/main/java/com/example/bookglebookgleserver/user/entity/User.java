package com.example.bookglebookgleserver.user.entity;


import jakarta.persistence.*;
import lombok.*;

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
    private String nickname;
    private String provider;
    private String profileImageUrl;

    @Column(name = "avg_rating")
    private Float avgRating;

    @Column(name = "rating_cnt")
    private Integer ratingCnt;

    @PrePersist
    public void prePersist() {
        if (avgRating == null) {
            avgRating = 3.0f;
        }
        if (ratingCnt == null) {
            ratingCnt = 0;
        }
    }

    public void setAvgRating(Float avgRating) {
        this.avgRating = avgRating;
    }
}
