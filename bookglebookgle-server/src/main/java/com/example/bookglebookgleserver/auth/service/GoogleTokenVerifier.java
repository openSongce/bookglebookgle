package com.example.bookglebookgleserver.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.util.Collections;

public class GoogleTokenVerifier {
    private static final String CLIENT_ID = "YOUR-WEB-CLIENT-ID"; // Google Cloud Console에서 받은 웹 클라이언트 ID

    public static GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(CLIENT_ID))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                return idToken.getPayload(); // 이메일, 이름 등 정보 포함
            } else {
                throw new IllegalArgumentException("Invalid ID Token.");
            }
        } catch (Exception e) {
            throw new RuntimeException("ID 토큰 검증 실패", e);
        }
    }
}
