package com.example.bookglebookgleserver.chat.service;

import com.example.bookglebookgleserver.chat.dto.ChatRoomSummaryDto;
import com.example.bookglebookgleserver.chat.entity.*;
import com.example.bookglebookgleserver.chat.repository.*;
import com.example.bookglebookgleserver.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    public List<ChatRoomSummaryDto> getChatRoomsForUser(User user) {
        // 1. 내가 속한 모든 채팅방 멤버 row 조회
        List<ChatRoomMember> myRooms = chatRoomMemberRepository.findByUser(user);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 2. 각 채팅방마다 정보 구성
        return myRooms.stream().map(roomMember -> {
            ChatRoom room = roomMember.getChatRoom();

            // 최신 메시지
            ChatMessage lastMessage = chatMessageRepository.findFirstByChatRoomOrderByCreatedAtDesc(room);

            // 아직 읽지 않은 메시지 개수 (내 lastReadMessageId보다 큰 id의 메시지 개수)
            int unreadCount = 0;
            if (roomMember.getLastReadMessageId() == null) {
                // 한 번도 읽은 적이 없다면, 모든 메시지 개수가 unread
                unreadCount = (int) chatMessageRepository.countByChatRoom(room);
            } else {
                unreadCount = chatMessageRepository.countByChatRoomAndIdGreaterThan(room, roomMember.getLastReadMessageId());
            }

            return ChatRoomSummaryDto.builder()
                    .groupId(room.getGroupId())
                    .groupTitle(room.getGroupTitle())
                    .imageUrl(room.getImageUrl())
                    .category(room.getCategory())
                    .lastMessage(lastMessage != null ? lastMessage.getContent() : null)
                    .lastMessageTime(lastMessage != null && lastMessage.getCreatedAt() != null ? lastMessage.getCreatedAt().format(formatter) : null)
                    .memberCount(chatRoomMemberRepository.countByChatRoom(room))
                    .unreadCount(unreadCount)
                    .build();
        }).collect(Collectors.toList());
    }
}
