package com.example.bookglebookgleserver.chat.dto;

import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import com.example.bookglebookgleserver.user.entity.User;

import java.time.LocalDateTime;

public record ChatMessageDto(
        Long id,
        Long userId,
        String userNickname,
        String message,
        LocalDateTime createdAt,
        String profileImgUrl,
        String profileColor
) {
    public static ChatMessageDto from(ChatMessage entity) {
        User sender = entity.getSender();
        return new ChatMessageDto(
                entity.getId(),
                entity.getSender().getId(),
                entity.getSender().getNickname(),
                entity.getContent(),
                entity.getCreatedAt(),
                sender.getProfileImageUrl(),
                sender.getProfileColor()
        );
    }
}
