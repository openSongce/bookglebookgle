package com.example.bookglebookgleserver.highlight.controller;

import com.example.bookglebookgleserver.highlight.dto.HighlightResponseDto;
import com.example.bookglebookgleserver.highlight.entity.Highlight;
import com.example.bookglebookgleserver.highlight.repository.HighlightRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PDF 하이라이트", description = "PDF 내 하이라이트(형광펜) 관련 API")
@RestController
@RequestMapping("/highlight")
@RequiredArgsConstructor
public class HighlightController {

    private final HighlightRepository highlightRepository;

    @Operation(
            summary = "그룹 전체 하이라이트 목록 조회",
            description = "특정 그룹 내 모든 PDF 하이라이트(형광펜) 정보를 조회합니다.",
            parameters = @Parameter(name = "groupId", description = "그룹 ID", example = "1"),
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Highlight.class)
                    )
            )
    )
    @GetMapping("/group/{groupId}")
    public List<HighlightResponseDto> getHighlights(@PathVariable Long groupId) {
        List<Highlight> highlights = highlightRepository.findByGroupId(groupId);
        return highlights.stream()
                .map(h -> new HighlightResponseDto(
                        h.getId(),
                        h.getGroupId(),
                        h.getUserId(),
                        h.getPage(),
                        h.getSnippet(),
                        h.getColor(),
                        h.getStartX(),
                        h.getStartY(),
                        h.getEndX(),
                        h.getEndY(),
                        h.getPdfFile().getPdfId(),
                        h.getPdfFile().getFileName(),
                        h.getCreatedAt().toString()
                ))
                .toList();
    }

}
