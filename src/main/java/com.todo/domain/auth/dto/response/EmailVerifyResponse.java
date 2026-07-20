package com.todo.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이메일 인증 코드 확인 응답")
public record EmailVerifyResponse(
        @Schema(description = "회원가입 시 사용할 인증 토큰 (3분 이내 사용)", example = "550e8400-e29b-41d4-a716-446655440000")
        String emailVerificationToken
) {}
