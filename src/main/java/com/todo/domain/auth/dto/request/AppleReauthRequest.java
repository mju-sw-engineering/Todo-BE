package com.todo.domain.auth.dto.request;

import com.todo.domain.auth.entity.ReauthPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Apple 계정 재인증 요청 — 비밀번호가 없는 소셜 계정용")
public record AppleReauthRequest(

        @NotBlank
        @Schema(description = "재인증을 위해 새로 받은 Apple identity token")
        String identityToken,

        @NotBlank
        @Schema(description = "identity token 발급에 사용한 원본 nonce")
        String nonce,

        @NotNull
        @Schema(description = "재인증으로 승인할 작업", example = "WITHDRAWAL")
        ReauthPurpose purpose
) {}
