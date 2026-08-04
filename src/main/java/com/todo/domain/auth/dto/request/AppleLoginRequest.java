package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank
        @Schema(description = "Apple이 발급한 identity token (JWT)")
        String identityToken,

        @NotBlank
        @Schema(description = "Apple이 발급한 authorization code (5분 유효)")
        String authorizationCode,

        @NotBlank
        @Schema(description = "클라이언트가 생성한 nonce (CSRF 방어)")
        String nonce
) {}
