package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AppleCompleteRequest(
        @NotBlank
        @Schema(description = "Apple 로그인 1단계에서 발급받은 setup token")
        String setupToken,

        @NotBlank
        @Schema(description = "사용자가 입력한 닉네임")
        String nickname
) {}
