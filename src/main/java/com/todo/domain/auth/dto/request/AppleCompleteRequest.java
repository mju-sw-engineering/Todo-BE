package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppleCompleteRequest(
        @NotBlank
        @Schema(description = "Apple 로그인 1단계에서 발급받은 setup token")
        String setupToken,

        @NotBlank
        @Schema(description = "사용자가 입력한 닉네임")
        String nickname,

        @Schema(description = "프로필 이미지 object key (presigned-upload로 발급 후 전달)", example = "profiles/1/uuid.png")
        String profileImageKey,

        @NotNull
        @AssertTrue(message = "이용약관에 동의해야 합니다")
        @Schema(description = "이용약관 동의 여부", example = "true")
        Boolean termsAgreed,

        @NotNull
        @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다")
        @Schema(description = "개인정보 처리방침 동의 여부", example = "true")
        Boolean privacyAgreed,

        @NotNull
        @Schema(description = "마케팅 수신 동의 여부 (선택)", example = "false")
        Boolean marketingAgreed
) {}
