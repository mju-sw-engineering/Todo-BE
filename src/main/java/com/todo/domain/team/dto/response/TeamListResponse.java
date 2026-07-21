package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TeamListResponse(
        @Schema(description = "팀 목록") List<TeamSummaryResponse> teams
) {
    public static TeamListResponse from(List<Team> teams) {
        return new TeamListResponse(
                teams.stream()
                        .map(TeamSummaryResponse::from)
                        .toList()
        );
    }
}
