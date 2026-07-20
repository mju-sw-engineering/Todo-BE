package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "회원가입 요청")
public class SignupRequest {

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
