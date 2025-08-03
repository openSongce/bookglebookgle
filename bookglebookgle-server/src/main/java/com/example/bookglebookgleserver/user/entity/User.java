package com.example.bookglebookgleserver.user.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Table(name="users")
public class User {

    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="user_id")
    private Long id;

    @Column(name="user_email",unique = true,nullable = false)
    private  String email;

    private String password;
    private String nickname;

    //로그인 제공자
    private String provider;

    private String profileImageUrl;


    @Column(name = "avg_rating")
    private Float avgRating;

    @Column(name = "rating_cnt")
    private Integer ratingCnt;

}
