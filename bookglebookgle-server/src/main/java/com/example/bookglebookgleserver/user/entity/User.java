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

    @Id@GeneratedValue
    @Column(name="user_id")
    private Long id;

    @Column(name="user_email",unique = true,nullable = false)
    private  String email;

    private String password;
    private String nickname;
}
