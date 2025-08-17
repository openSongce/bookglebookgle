package com.example.bookglebookgleserver.common.util;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailSender {
    private  final JavaMailSender mailSender;

    public void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);  // true: HTML 형식

            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("❌ 이메일 전송 실패 (확장 캐치): " + e.getClass().getName());
            System.out.println("❌ 예외 메시지: " + e.getMessage());

            // 강제 출력 추가
            e.printStackTrace(System.out);
        }
    }

}
