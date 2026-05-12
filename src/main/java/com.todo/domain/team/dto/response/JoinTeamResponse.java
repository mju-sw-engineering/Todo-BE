package com.todo.domain.team.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record JoinTeamResponse(
        @Schema(description = "팀 ID") Long teamId
) {}
