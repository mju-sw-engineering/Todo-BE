package com.todo.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AppleSetupResponse(
        @Schema(description = "닉네임 입력 후 /apple/complete에 제출할 임시 토큰 (5분 유효)")
        String setupToken
) {}
