package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @NotBlank @Schema(description = "로그인 아이디", example = "user123") String loginId,
        @NotBlank @Schema(description = "비밀번호", example = "password123!") String password
) {}
