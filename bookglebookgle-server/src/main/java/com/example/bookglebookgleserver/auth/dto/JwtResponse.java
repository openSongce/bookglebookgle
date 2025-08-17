package com.example.bookglebookgleserver.auth.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JwtResponse {
    private String accessToken;
    private String refreshToken;

    private String email;
    private String nickname;
    private String profileImageUrl;

    private Long userId;
    private Float avgRating;

    public JwtResponse(String accessToken,String refreshToken){
        this.accessToken=accessToken;
        this.refreshToken=refreshToken;
    }

}
