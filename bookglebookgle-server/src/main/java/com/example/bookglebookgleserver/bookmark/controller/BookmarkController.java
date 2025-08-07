package com.example.bookglebookgleserver.bookmark.controller;

import com.example.bookglebookgleserver.auth.security.CustomUserDetails;
import com.example.bookglebookgleserver.bookmark.dto.BookmarkCreateRequestDto;
import com.example.bookglebookgleserver.bookmark.dto.BookmarkResponse;
import com.example.bookglebookgleserver.bookmark.service.BookmarkService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "북마크", description = "북마크 기능 API")
@RestController
@RequestMapping("/bookmark")
@RequiredArgsConstructor
public class BookmarkController {
    private final BookmarkService bookmarkService;

    @Operation(
            summary = "북마크 생성",
            description = "특정 그룹의 지정한 페이지에 북마크를 추가합니다."
    )
    @PostMapping("/create")
    public ResponseEntity<BookmarkResponse> createBookmark(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUser,
            @RequestBody BookmarkCreateRequestDto request
    ) {
        User user = customUser.getUser();
        BookmarkResponse response = bookmarkService.createBookmark(user.getId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "내 북마크 전체 목록 조회",
            description = "현재 로그인한 사용자가 등록한 모든 북마크를 조회합니다."
    )
    @GetMapping("/list")
    public ResponseEntity<List<BookmarkResponse>> getMyBookmarkList(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUser
    ) {
        User user = customUser.getUser();
        List<BookmarkResponse> bookmarkList = bookmarkService.getUserBookmarkList(user.getId());
        return ResponseEntity.ok(bookmarkList);
    }
}
