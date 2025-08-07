package com.example.bookglebookgleserver.comment.controller;

import com.example.bookglebookgleserver.comment.dto.CommentResponseDto;
import com.example.bookglebookgleserver.comment.entity.Comment;
import com.example.bookglebookgleserver.comment.repository.CommentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "댓글(Comment)", description = "PDF 페이지 주석/댓글 API")
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository commentRepository;

    @Operation(
            summary = "그룹 내 전체 댓글(주석) 조회",
            description = "특정 그룹에 작성된 모든 댓글(주석)을 조회합니다.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CommentResponseDto.class))
            )
    )
    @GetMapping("/group/{groupId}")
    public List<CommentResponseDto> getComments(@PathVariable Long groupId) {
        List<Comment> comments = commentRepository.findByGroupId(groupId);
        return comments.stream()
                .map(c -> new CommentResponseDto(
                        c.getId(),
                        c.getPdfFile().getPdfId(),
                        c.getGroupId(),
                        c.getUserId(),
                        c.getPage(),
                        c.getSnippet(),
                        c.getText(),
                        c.getStartX(),
                        c.getStartY(),
                        c.getEndX(),
                        c.getEndY(),
                        c.getCreatedAt().toString()
                ))
                .toList();
    }

}
