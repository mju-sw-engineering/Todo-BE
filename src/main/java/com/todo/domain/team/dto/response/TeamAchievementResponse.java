package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

public record TeamAchievementResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "누적 성공 투두 수") int successCount
) {
    public static TeamAchievementResponse from(Team team) {
        return new TeamAchievementResponse(
                team.getId(),
                team.getSuccessCount()
        );
    }
}
