package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.AiPersona;
import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateTeamPersonaResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "AI 평가 페르소나") AiPersona aiPersona
) {
    public static UpdateTeamPersonaResponse from(Team team) {
        return new UpdateTeamPersonaResponse(
                team.getId(),
                team.getAiPersona()
        );
    }
}
