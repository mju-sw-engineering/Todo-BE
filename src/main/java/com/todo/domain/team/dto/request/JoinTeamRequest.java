package com.todo.domain.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record JoinTeamRequest(
        @NotBlank(message = "초대 코드를 입력해주세요")
        @Schema(description = "팀 초대 코드", example = "ABCD1234")
        String inviteCode
) {}
