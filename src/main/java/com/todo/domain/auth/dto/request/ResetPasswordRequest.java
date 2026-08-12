package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 토큰으로 비밀번호 변경 요청")
public record ResetPasswordRequest(
        @NotBlank
        @Schema(description = "find-password로 발급받은 비밀번호 재설정 토큰")
        String passwordResetToken,

        @NotBlank
        @Schema(description = "새 비밀번호")
        String newPassword,

        @NotBlank
        @Schema(description = "새 비밀번호 확인")
        String newPasswordConfirm
) {}
