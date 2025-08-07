package com.example.bookglebookgleserver.chat.service;

import com.example.bookglebookgleserver.chat.dto.ChatMessageDto;
import com.example.bookglebookgleserver.chat.dto.ChatRoomSummaryDto;
import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.chat.entity.ChatRoomMember;
import com.example.bookglebookgleserver.chat.repository.ChatMessageRepository;
import com.example.bookglebookgleserver.chat.repository.ChatRoomMemberRepository;
import com.example.bookglebookgleserver.chat.repository.ChatRoomRepository;
import com.example.bookglebookgleserver.global.exception.ForbiddenException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.user.entity.User;
import jakarta.transaction.Transactional;
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
    private final ChatRoomRepository chatRoomRepository;

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
            int unreadCount;
            if (roomMember.getLastReadMessageId() == null) {
                unreadCount = chatMessageRepository.countByChatRoom(room);
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

    public List<ChatMessageDto> getMessagesByRoomIdAndBeforeId(Long roomId, Long beforeId, int size) {
        // 반드시 ChatRoom 엔티티를 조회해서 사용
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("채팅방이 존재하지 않습니다."));

        List<ChatMessage> messages;
        if (beforeId == null) {
            // 최초 조회 (최신 N개)
            messages = chatMessageRepository.findTop30ByChatRoomOrderByIdDesc(chatRoom);
        } else {
            // 커서 조회 (이전 N개)
            messages = chatMessageRepository.findTop30ByChatRoomAndIdLessThanOrderByIdDesc(chatRoom, beforeId);
        }

        return messages.stream()
                .map(ChatMessageDto::from)  // 엔티티→DTO 변환 메서드 필요
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAllMessagesAsRead(User user, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("채팅방이 존재하지 않습니다."));
        ChatRoomMember member = chatRoomMemberRepository.findByUserAndChatRoom(user, chatRoom)
                .orElseThrow(() -> new NotFoundException("채팅방 멤버 정보가 없습니다."));
        ChatMessage lastMsg = chatMessageRepository.findFirstByChatRoomOrderByCreatedAtDesc(chatRoom);
        if (lastMsg != null) {
            member.setLastReadMessageId(lastMsg.getId());
            chatRoomMemberRepository.save(member);
        }
    }
}
