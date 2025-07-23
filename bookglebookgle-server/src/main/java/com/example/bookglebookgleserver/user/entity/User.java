package com.example.bookglebookgleserver.user.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name="users")
public class User {

    @Id@GeneratedValue
    @Column(name="user_id")
    private Long id;

    @Column(name="user_email",unique = true,nullable = false)
    private  String email;

    private String password;
}
