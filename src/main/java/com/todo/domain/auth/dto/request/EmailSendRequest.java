package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 인증 코드 발송 요청")
public record EmailSendRequest(
        @NotBlank
        @Email(message = "올바른 이메일 형식이 아닙니다")
        @Schema(description = "인증할 이메일 주소", example = "user@example.com")
        String email
) {}
