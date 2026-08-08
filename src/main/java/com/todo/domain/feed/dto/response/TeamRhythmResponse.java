package com.todo.domain.feed.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TeamRhythmResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "팀 이름") String teamName,
        @Schema(description = "팀원 수") int memberCount,
        @Schema(description = "팀 연속 활동 일수") int streakDays,
        @Schema(description = "오래된 주 → 최신 주 순서. 마지막이 이번 주") List<TeamWeekRhythmResponse> weeks,
        @Schema(description = "오늘 기록을 남긴 팀원") List<TeamRhythmMemberResponse> todayMembers
) {
    public static TeamRhythmResponse from(
            Team team,
            int memberCount,
            int streakDays,
            List<TeamWeekRhythmResponse> weeks,
            List<TeamRhythmMemberResponse> todayMembers
    ) {
        return new TeamRhythmResponse(
                team.getId(),
                team.getTeamName(),
                memberCount,
                streakDays,
                weeks,
                todayMembers
        );
    }
}
