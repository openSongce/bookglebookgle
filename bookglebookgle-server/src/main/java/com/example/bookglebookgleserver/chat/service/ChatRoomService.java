package com.example.bookglebookgleserver.chat.service;

import com.example.bookglebookgleserver.chat.dto.ChatMessageDto;
import com.example.bookglebookgleserver.chat.dto.ChatRoomSummaryDto;
import com.example.bookglebookgleserver.chat.entity.ChatMessage;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.chat.entity.ChatRoomMember;
import com.example.bookglebookgleserver.chat.repository.ChatMessageRepository;
import com.example.bookglebookgleserver.chat.repository.ChatRoomMemberRepository;
import com.example.bookglebookgleserver.chat.repository.ChatRoomRepository;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    private static final DateTimeFormatter LAST_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 내가 참여 중인 채팅방 목록을 서버에서 정렬(pinned DESC → lastMessageAt DESC, null은 아래)해서 반환.
     * unreadCount는 NORMAL 메시지만 대상으로 계산.
     */
    @Transactional
    public List<ChatRoomSummaryDto> getChatRoomsForUser(User user) {
        var rooms = chatRoomRepository.findRoomsForUserSorted(user);

        return rooms.stream().map(room -> {
            long unread = 0L;

            // 내 멤버십 조회
            var memberOpt = chatRoomMemberRepository.findByUserAndChatRoom(user, room);
            if (memberOpt.isPresent()) {
                Long lastReadId = memberOpt.get().getLastReadMessageId();
                if (lastReadId == null) lastReadId = 0L;

                // NORMAL 기준 unread 계산
                unread = chatMessageRepository.countUnreadAfter(
                        room.getGroupId(),
                        lastReadId,
                        ChatMessage.Type.NORMAL
                );
            }

            return ChatRoomSummaryDto.builder()
                    .groupId(room.getGroupId())
                    .groupTitle(room.getGroupTitle())
                    .imageUrl(room.getImageUrl())
                    .category(room.getCategory())
                    .lastMessage(room.getLastMessage())
                    .lastMessageTime(room.getLastMessageAt() != null
                            ? room.getLastMessageAt().format(LAST_TIME_FMT)
                            : null)
                    .memberCount(room.getMemberCount())
                    .unreadCount(safeToInt(unread))
                    .build();
        }).toList();
    }

    /**
     * 메시지 커서 기반 조회(최신순). beforeId 없으면 최신부터 size개, 있으면 그 이전에서 size개.
     */
    @Transactional
    public List<ChatMessageDto> getMessagesByRoomIdAndBeforeId(Long roomId, Long beforeId, int size) {
        // room 존재 검증(권한 체크는 컨트롤러/필터에서 했다고 가정)
        chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("채팅방이 존재하지 않습니다."));

        var page = PageRequest.of(0, size);
        var list = chatMessageRepository.findByRoomIdWithCursor(roomId, beforeId, page);

        // 기존 팩토리 사용
        return list.stream().map(ChatMessageDto::from).toList();
    }

    /**
     * 읽음 처리: 사용자의 lastReadMessageId를 방의 lastMessageId로 맞춘다.
     */
    @Transactional
    public void markAllMessagesAsRead(User user, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("채팅방이 존재하지 않습니다."));
        ChatRoomMember member = chatRoomMemberRepository.findByUserAndRoomId(user, roomId)
                .orElseThrow(() -> new NotFoundException("채팅방 멤버 정보가 없습니다."));

        Long lastMsgId = room.getLastMessageId();
        if (lastMsgId == null) lastMsgId = 0L;

        member.setLastReadMessageId(lastMsgId);
        chatRoomMemberRepository.save(member);
    }

    /**
     * (헬퍼) 일반 채팅 저장 직후 방의 정렬/미리보기 필드를 갱신한다.
     * 토론/퀴즈 방송 이벤트는 저장 자체가 없으므로 호출하지 말 것.
     */
    @Transactional
    public void bumpRoomAfterNormalMessage(ChatMessage saved) {
        ChatRoom room = saved.getChatRoom();
        room.setLastMessageId(saved.getId());
        room.setLastMessageAt(saved.getCreatedAt());
        room.setLastMessage(trimPreview(saved.getContent(), 50));
        chatRoomRepository.save(room);
    }

    private static String trimPreview(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static int safeToInt(long v) {
        return (v > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) v;
    }
}
