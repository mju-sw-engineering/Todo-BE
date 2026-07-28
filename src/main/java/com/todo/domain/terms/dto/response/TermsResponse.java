package com.todo.domain.terms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TermsResponse(
        @Schema(description = "약관 유형", example = "TERMS")
        String type,

        @Schema(description = "약관 제목", example = "이용약관")
        String title,

        @Schema(description = "약관 내용")
        String content,

        @Schema(description = "약관 버전", example = "v1.0")
        String version,

        @Schema(description = "시행일", example = "2026-07-28")
        String updatedAt
) {}
