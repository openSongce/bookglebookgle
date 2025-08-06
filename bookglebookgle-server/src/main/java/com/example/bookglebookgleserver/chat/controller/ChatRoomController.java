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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "채팅방", description = "채팅방 관련 API")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
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
    public ResponseEntity<List<ChatRoomSummaryDto>> getChatRoomList(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        List<ChatRoomSummaryDto> response = chatRoomService.getChatRoomsForUser(user);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "채팅방 메시지 조회(커서 기반)",
            description = "특정 채팅방의 메시지를 beforeId(이전 메시지 PK) 기준으로 페이지네이션해서 조회합니다. 최신순 N개 반환."
    )
    @GetMapping("/{roomId}/messages")
    public List<ChatMessageDto> getChatMessages(
            @Parameter(description = "채팅방 ID") @PathVariable Long roomId,
            @Parameter(description = "이전 메시지의 PK (null이면 최신 N개)") @RequestParam(required = false) Long beforeId,
            @Parameter(description = "한 번에 불러올 메시지 개수", example = "20") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails customUser  // (권한 검증용, 옵션)
    ) {
        // 권한 검증 등 필요하면 customUser.getUser() 활용
        return chatRoomService.getMessagesByRoomIdAndBeforeId(roomId, beforeId, size);
    }
}

