package com.todo.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청")
public record DeleteUserRequest(

        @NotBlank
        @Schema(description = "POST /api/auth/reauth 로 발급받은 재인증 토큰")
        String reauthToken
) {}
