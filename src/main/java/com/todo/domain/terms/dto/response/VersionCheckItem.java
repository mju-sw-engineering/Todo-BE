package com.todo.domain.terms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record VersionCheckItem(
        @Schema(description = "동의한 버전, 미동의 시 null", example = "v1.0")
        String agreedVersion,

        @Schema(description = "현재 최신 버전", example = "v1.1")
        String latestVersion,

        @Schema(description = "재동의 필요 여부")
        boolean needsConsent
) {}
