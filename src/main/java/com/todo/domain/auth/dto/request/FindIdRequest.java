package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "아이디 찾기 요청")
public record FindIdRequest(
        @NotBlank
        @Email(message = "올바른 이메일 형식이 아닙니다")
        @Schema(description = "인증된 이메일 주소", example = "user@example.com")
        String email,

        @NotBlank
        @Schema(description = "이메일 인증 토큰 (email/verify로 발급)", example = "550e8400-e29b-41d4-a716-446655440000")
        String emailVerificationToken
) {}
