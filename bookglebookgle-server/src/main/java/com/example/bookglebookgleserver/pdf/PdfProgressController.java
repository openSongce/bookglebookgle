package com.example.bookglebookgleserver.pdf;


import com.example.bookglebookgleserver.pdf.dto.PdfProgressResponse;
import com.example.bookglebookgleserver.pdf.service.PdfService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/groups")
public class PdfProgressController {

    private final PdfService pdfService;

    @Operation(
            summary = "PDF 진도 조회",
            description = "특정 그룹에서 사용자가 마지막으로 읽은 PDF 페이지와 전체 페이지 수, 진도율을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "진도 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청입니다"),
            @ApiResponse(responseCode = "404", description = "그룹 또는 사용자 정보를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @GetMapping("/{groupId}/progress")
    public ResponseEntity<PdfProgressResponse> getProgress(
            @PathVariable Long groupId,
            @AuthenticationPrincipal User currentUser
    ) {
        PdfProgressResponse response = pdfService.getProgress(currentUser, groupId);
        return ResponseEntity.ok(response);
    }
}
