package com.todo.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateNicknameRequest(
        @NotBlank(message = "닉네임은 필수입니다")
        @Schema(description = "변경할 닉네임", example = "새닉네임")
        String nickname
) {
}
