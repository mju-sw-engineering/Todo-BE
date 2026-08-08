package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

public record TeamSummaryResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "팀 이름") String teamName,
        @Schema(description = "팀 설명") String description,
        @Schema(description = "팀 이미지 URL") String teamImageUrl
) {
    public static TeamSummaryResponse from(Team team) {
        return new TeamSummaryResponse(
                team.getId(),
                team.getTeamName(),
                team.getDescription(),
                team.getTeamImage()
        );
    }

    public TeamSummaryResponse withImageUrl(String resolvedImageUrl) {
        return new TeamSummaryResponse(teamId, teamName, description, resolvedImageUrl);
    }
}
