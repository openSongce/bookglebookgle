package com.example.bookglebookgleserver.pdf;


import com.example.bookglebookgleserver.pdf.dto.PdfProgressResponse;
import com.example.bookglebookgleserver.pdf.service.PdfService;
import com.example.bookglebookgleserver.user.entity.User;
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

    @GetMapping("/{groupId}/progress")
    public ResponseEntity<PdfProgressResponse> getProgress(
            @PathVariable Long groupId,
            @AuthenticationPrincipal User currentUser
    ) {
        PdfProgressResponse response = pdfService.getProgress(currentUser, groupId);
        return ResponseEntity.ok(response);
    }
}
