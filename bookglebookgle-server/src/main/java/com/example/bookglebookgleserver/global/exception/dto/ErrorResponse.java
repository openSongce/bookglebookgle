package com.example.bookglebookgleserver.global.exception.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 코드", example = "BAD_REQUEST")
        String code,

        @Schema(description = "에러 메시지", example = "업로드된 파일이 비어 있습니다.")
        String message
) {}
