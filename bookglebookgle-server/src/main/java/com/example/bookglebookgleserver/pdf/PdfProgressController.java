package com.example.bookglebookgleserver.pdf;


import com.example.bookglebookgleserver.pdf.dto.PdfProgressResponse;
import com.example.bookglebookgleserver.pdf.service.PdfService;
import com.example.bookglebookgleserver.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@Tag(name = "PDF Progress", description = "그룹 내 PDF 읽기 진도 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/groups")
public class PdfProgressController {

    private final PdfService pdfService;

    @Operation(
            summary = "PDF 진도 조회",
            description = "특정 그룹에서 사용자가 지금까지 도달한 최댓값 페이지와 전체 페이지 수, 진도율을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "진도 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PdfProgressResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 예시",
                                    // ⚠️ 아래 키들은 실제 PdfProgressResponse 필드명에 맞춰 수정하세요!
                                    value = """
                    {
                      "groupId": 12,
                      "maxReadPage": 37,
                      "totalPages": 240,
                      "progressRate": 15.42
                    }
                    """
                            )
                    )
            ),
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
