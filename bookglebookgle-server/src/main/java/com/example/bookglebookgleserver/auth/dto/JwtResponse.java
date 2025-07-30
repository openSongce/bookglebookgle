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

    //추가필드
    private String email;
    private String nickname;
    private String profileImageUrl;

    public JwtResponse(String accessToken,String refreshToken){
        this.accessToken=accessToken;
        this.refreshToken=refreshToken;
    }

}
