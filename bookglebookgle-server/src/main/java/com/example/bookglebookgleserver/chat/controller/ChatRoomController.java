package com.example.bookglebookgleserver.chat.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.chat.dto.ChatRoomSummaryDto;
import com.example.bookglebookgleserver.chat.service.ChatRoomService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

