package com.todo.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 상태에서 현재 비밀번호를 확인하고 비밀번호를 변경하는 요청")
public record UpdatePasswordRequest(
        @NotBlank
        @Schema(description = "현재 비밀번호")
        String currentPassword,

        @NotBlank
        @Schema(description = "새 비밀번호")
        String newPassword,

        @NotBlank
        @Schema(description = "새 비밀번호 확인")
        String newPasswordConfirm
) {}
