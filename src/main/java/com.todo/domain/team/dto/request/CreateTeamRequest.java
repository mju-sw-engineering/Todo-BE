package com.todo.domain.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(
        @NotBlank(message = "팀 이름을 입력해주세요")
        @Schema(description = "팀 이름", example = "우리팀")
        String teamName
) {}
