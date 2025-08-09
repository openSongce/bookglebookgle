package com.example.bookglebookgleserver.fcm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.base64:}")
    private String base64Creds;

    @Value("${firebase.credentials.file-path:classpath:service-account.json}")
    private Resource fileResource;

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        InputStream credStream;
        if (base64Creds != null && !base64Creds.isBlank()) {
            credStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Creds));
            log.info("🔐 Firebase 초기화: BASE64 자격증명 사용");
        } else {
            credStream = fileResource.getInputStream();
            log.info("🔐 Firebase 초기화: 파일 자격증명 사용 ({})", fileResource);
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(credStream))
                .build();

        List<FirebaseApp> apps = FirebaseApp.getApps();
        if (apps != null && !apps.isEmpty()) {
            log.info("✅ Firebase 초기화 완료(기존 인스턴스 재사용)");
            return apps.get(0);
        }
        log.info("✅ Firebase 초기화 완료(신규 인스턴스)");
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        log.info("📮 FirebaseMessaging 빈 등록 완료");
        return FirebaseMessaging.getInstance(app);
    }
}
