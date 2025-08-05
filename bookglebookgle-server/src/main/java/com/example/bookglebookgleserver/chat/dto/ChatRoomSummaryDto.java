package com.example.bookglebookgleserver.chat.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRoomSummaryDto {
    private Long groupId;
    private String groupTitle;
    private String imageUrl;
    private String category;
    private String lastMessage;
    private String lastMessageTime; // LocalDateTime이면 String으로 변환해서 반환
    private int memberCount;
    private int unreadCount;
}
