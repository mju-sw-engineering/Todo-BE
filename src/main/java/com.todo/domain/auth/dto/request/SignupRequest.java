package com.todo.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest {

    @NotBlank
    private String loginId;

    @NotBlank
    private String password;

    @NotBlank
    private String passwordConfirm;

    @NotBlank
    private String nickname;
}
