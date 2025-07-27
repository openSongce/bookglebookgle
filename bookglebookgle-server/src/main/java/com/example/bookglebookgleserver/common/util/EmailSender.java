package com.example.bookglebookgleserver.common.util;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;




//
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
            System.out.println("이메일 전송 완료");
        } catch (MessagingException e) {
            System.err.println(" 이메일 전송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }






}
