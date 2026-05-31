package com.todo.domain.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "팀 이메일 초대 요청")
public record InviteTeamRequest(
        @NotEmpty(message = "초대할 이메일을 입력해주세요")
        @Schema(description = "초대 이메일 목록", example = "[\"member1@example.com\", \"member2@example.com\"]")
        List<
                @NotBlank(message = "이메일을 입력해주세요")
                @Email(message = "올바른 이메일 형식이어야 합니다")
                String> emails
) {}
