package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.group.dto.CompletedBookDto;
import com.example.bookglebookgleserver.group.service.GroupService;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;



@RestController
@RequestMapping("/shelf")
@RequiredArgsConstructor
public class ShelfController {

    private final GroupService groupService;
    private final UserRepository userRepository;

    @Operation(summary = "완독(100%) PDF 목록", description = "pdf_file.file_name과 group.category를 반환")
    @GetMapping("/completed")
    public List<CompletedBookDto> getCompletedBooks(
            @AuthenticationPrincipal(expression = "username") String email
    ) {
        if (email == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        return groupService.getCompletedBooks(me.getId());
    }
}
