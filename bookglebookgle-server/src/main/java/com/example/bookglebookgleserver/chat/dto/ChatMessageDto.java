package com.example.bookglebookgleserver.chat.dto;

import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageDto(
        Long id,
        Long userId,
        String userNickname,
        String message,
        LocalDateTime createdAt
) {
    public static ChatMessageDto from(ChatMessage entity) {
        return new ChatMessageDto(
                entity.getId(),
                entity.getSender().getId(),
                entity.getSender().getNickname(),
                entity.getContent(),
                entity.getCreatedAt()
        );
    }
}
