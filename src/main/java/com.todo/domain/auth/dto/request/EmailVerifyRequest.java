package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 인증 코드 확인 요청")
public record EmailVerifyRequest(
        @NotBlank
        @Email(message = "올바른 이메일 형식이 아닙니다")
        @Schema(description = "인증할 이메일 주소", example = "user@example.com")
        String email,

        @NotBlank
        @Size(min = 6, max = 6, message = "인증 코드는 6자리입니다")
        @Schema(description = "수신한 6자리 인증 코드", example = "123456")
        String code
) {}
