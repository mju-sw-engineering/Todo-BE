package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record AppleCompleteRequest(
        @NotBlank
        @Schema(description = "Apple 로그인 1단계에서 발급받은 setup token")
        String setupToken,

        @NotBlank
        @Schema(description = "사용자가 입력한 닉네임")
        String nickname,

        @Schema(description = "업로드한 프로필 이미지의 object key (선택)")
        String profileImageKey,

        @AssertTrue(message = "이용약관에 동의해야 합니다")
        @Schema(description = "이용약관 동의 (필수)")
        Boolean termsAgreed,

        @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다")
        @Schema(description = "개인정보 처리방침 동의 (필수)")
        Boolean privacyAgreed,

        @Schema(description = "마케팅 정보 수신 동의 (선택)")
        Boolean marketingAgreed,

        @Schema(description = "클라이언트가 생성한 기기 고유 ID (선택, 세션 목록/기기별 관리에 사용)")
        String deviceId
) {}
