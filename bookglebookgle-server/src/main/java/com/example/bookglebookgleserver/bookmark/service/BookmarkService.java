package com.example.bookglebookgleserver.bookmark.service;

import com.example.bookglebookgleserver.bookmark.dto.BookmarkCreateRequestDto;
import com.example.bookglebookgleserver.bookmark.dto.BookmarkResponse;
import com.example.bookglebookgleserver.bookmark.entity.Bookmark;
import com.example.bookglebookgleserver.bookmark.repository.BookmarkRepository;
import com.example.bookglebookgleserver.global.exception.BadRequestException;
import com.example.bookglebookgleserver.global.exception.NotFoundException;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public BookmarkResponse createBookmark(Long userId, BookmarkCreateRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));
        Group group = groupRepository.findById(request.groupId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 그룹입니다."));

        bookmarkRepository.findByUserAndGroupAndPage(user, group, request.page())
                .ifPresent(b -> {
                    throw new BadRequestException("이미 해당 페이지에 북마크가 존재합니다.");
                });

        Bookmark bookmark = Bookmark.builder()
                .user(user)
                .group(group)
                .page(request.page())
                .build();
        bookmarkRepository.save(bookmark);

        return new BookmarkResponse(
                bookmark.getId(),
                bookmark.getPage(),
                bookmark.getCreatedAt().toString()
        );
    }

    @Transactional
    public List<BookmarkResponse> getUserBookmarkList(Long userId) {
        // userId로 User 객체 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다."));

        // 해당 유저의 전체 북마크 조회
        List<Bookmark> bookmarks = bookmarkRepository.findByUser(user);

        // 엔티티 → DTO 변환
        return bookmarks.stream()
                .map(bookmark -> new BookmarkResponse(
                        bookmark.getId(),
                        bookmark.getPage(),
                        bookmark.getCreatedAt().toString()
                ))
                .toList();
    }

}

