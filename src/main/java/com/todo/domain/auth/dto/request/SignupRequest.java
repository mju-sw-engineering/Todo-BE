package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "회원가입 요청")
public class SignupRequest {

    @NotBlank
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Schema(description = "인증된 이메일 주소", example = "user@example.com")
    private String email;

    @NotBlank
    @Schema(description = "이메일 인증 토큰 (email/verify로 발급)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String emailVerificationToken;

    @NotBlank
    @Schema(description = "로그인 아이디", example = "user123")
    private String loginId;

    @NotBlank
    @Schema(description = "비밀번호", example = "password123!")
    private String password;

    @NotBlank
    @Schema(description = "비밀번호 확인", example = "password123!")
    private String passwordConfirm;

    @NotBlank
    @Schema(description = "닉네임", example = "홍길동")
    private String nickname;

    @Schema(description = "프로필 이미지 object key (presigned-upload로 발급 후 전달)", example = "profiles/1/uuid.png")
    private String profileImageKey;

    @NotNull
    @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다")
    @Schema(description = "개인정보 처리방침 동의 여부", example = "true")
    private Boolean termsAgreed;
}
