package com.example.bookglebookgleserver.chat.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.chat.dto.ChatMessageDto;
import com.example.bookglebookgleserver.chat.dto.ChatRoomSummaryDto;
import com.example.bookglebookgleserver.chat.service.ChatRoomService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "채팅방", description = "채팅방 관련 API")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Validated
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @Operation(
            summary = "내가 참여 중인 채팅방 목록 조회",
            description = """
            현재 로그인한 사용자가 참여 중인 모든 채팅방 정보를 조회합니다.<br>
            각 채팅방마다 마지막 메시지, 멤버 수, 안읽은 메시지 개수가 포함됩니다.<br>
            JWT 인증 필요 (Authorization 헤더)
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomSummaryDto>> getChatRoomList(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userDetails.getUser();
        List<ChatRoomSummaryDto> response = chatRoomService.getChatRoomsForUser(user);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "채팅방 메시지 조회 (커서 기반, 최신순)",
            description = """
            특정 채팅방의 메시지를 커서 기반으로 조회합니다.<br>
            - <b>beforeId</b>가 null 또는 미전달 시: 최신 메시지 <b>size</b>개 반환<br>
            - <b>beforeId</b>가 전달되면: 해당 id보다 작은(이전) 메시지 <b>size</b>개 반환<br>
            (무한 스크롤 구현 가능, 기본 20개)<br>
            JWT 인증 필요 (Authorization 헤더)
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "채팅방 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{roomId}/messages")
    public List<ChatMessageDto> getChatMessages(
            @Parameter(description = "채팅방 ID", example = "1") @PathVariable Long roomId,
            @Parameter(description = "커서: 이 id보다 작은 메시지만 조회 (최초 조회 시 null)") @RequestParam(required = false) Long beforeId,
            @Parameter(description = "한 번에 불러올 메시지 개수", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        if (customUser == null) { // 401 가드
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        User me = customUser.getUser();
        return chatRoomService.getMessagesByRoomIdAndBeforeId(me, roomId, beforeId, size); //  현재 유저 전달
    }

    @Operation(
            summary = "채팅방 입장/읽음 처리",
            description = """
        사용자가 특정 채팅방에 입장하거나, 메시지를 모두 읽은 경우 호출하는 API입니다.<br>
        이 API를 호출하면 사용자의 읽지 않은 메시지 수가 0으로 초기화됩니다.<br>
        JWT 인증 필요 (Authorization 헤더)<br>
        (Android: 채팅방 입장, 스크롤 맨 아래까지 내릴 때 호출)
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "채팅방 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "채팅방 ID", example = "1") @PathVariable Long roomId
    ) {
        User user = userDetails.getUser();
        chatRoomService.markAllMessagesAsRead(user, roomId);
        return ResponseEntity.ok().build();
    }
}
