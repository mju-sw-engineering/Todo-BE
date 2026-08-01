package com.todo.domain.auth.dto.request;

import com.todo.domain.auth.entity.ReauthPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "재인증 요청")
public record ReauthRequest(

        @NotBlank
        @Schema(description = "현재 비밀번호", example = "password123!")
        String password,

        @NotNull
        @Schema(description = "재인증으로 승인할 작업", example = "WITHDRAWAL")
        ReauthPurpose purpose
) {}
