package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "회원가입 요청")
public record SignupRequest(

        @NotBlank
        @Email(message = "올바른 이메일 형식이 아닙니다")
        @Schema(description = "인증된 이메일 주소", example = "user@example.com")
        String email,

        @NotBlank
        @Schema(description = "이메일 인증 토큰 (email/verify로 발급)", example = "550e8400-e29b-41d4-a716-446655440000")
        String emailVerificationToken,

        @NotBlank
        @Schema(description = "로그인 아이디", example = "user123")
        String loginId,

        @NotBlank
        @Schema(description = "비밀번호", example = "password123!")
        String password,

        @NotBlank
        @Schema(description = "비밀번호 확인", example = "password123!")
        String passwordConfirm,

        @NotBlank
        @Schema(description = "닉네임", example = "홍길동")
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
