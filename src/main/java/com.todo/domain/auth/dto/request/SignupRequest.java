package com.todo.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
}
