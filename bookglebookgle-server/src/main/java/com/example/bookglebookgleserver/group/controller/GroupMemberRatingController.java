package com.example.bookglebookgleserver.group.controller;

import com.example.bookglebookgleserver.group.service.GroupMemberRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "그룹 멤버 평가", description = "그룹 내 멤버간 평가(점수) API")
@RestController
@RequestMapping("/group/{groupId}/rating")
@RequiredArgsConstructor
public class GroupMemberRatingController {

    private final GroupMemberRatingService ratingService;

    @Operation(summary = "멤버 평가 등록", description = "같은 그룹 내 타인 평가 (중복, 본인 평가 불가)")
    @PostMapping
    public ResponseEntity<String> addRating(
            @PathVariable Long groupId,
            @RequestParam Long fromId,
            @RequestParam Long toId,
            @RequestParam float score
    ) {
        ratingService.addRating(groupId, fromId, toId, score);
        return ResponseEntity.ok("평가 등록 완료");
    }

    @Operation(summary = "멤버 평가 수정", description = "이미 평가한 대상만 수정 가능")
    @PutMapping
    public ResponseEntity<String> updateRating(
            @PathVariable Long groupId,
            @RequestParam Long fromId,
            @RequestParam Long toId,
            @RequestParam float score
    ) {
        ratingService.updateRating(groupId, fromId, toId, score);
        return ResponseEntity.ok("평가 수정 완료");
    }

    @Operation(summary = "내가 받은 그룹 내 평균 평점 조회")
    @GetMapping("/to/{toId}/average")
    public ResponseEntity<Float> getAverage(
            @PathVariable Long groupId,
            @PathVariable Long toId
    ) {
        Float avg = ratingService.getAverageRating(groupId, toId);
        return ResponseEntity.ok(avg);
    }
}
