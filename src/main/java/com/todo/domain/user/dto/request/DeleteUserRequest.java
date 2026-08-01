package com.todo.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청")
public record DeleteUserRequest(

        @NotBlank
        @Schema(description = "본인 확인용 비밀번호", example = "password123!")
        String password
) {}
