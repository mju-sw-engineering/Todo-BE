package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

public record JoinTeamResponse(
        @Schema(description = "팀 ID") Long teamId
) {
    public static JoinTeamResponse from(Team team) {
        return new JoinTeamResponse(team.getId());
    }
}
