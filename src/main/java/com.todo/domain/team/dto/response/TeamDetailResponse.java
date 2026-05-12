package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TeamDetailResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "팀 이름") String teamName,
        @Schema(description = "팀 이미지 URL") String teamImageUrl,
        @Schema(description = "팀원 수") int memberCount,
        @Schema(description = "성공 개수") int successCount,
        @Schema(description = "연속 todo 횟수") int continuousTodoCount,
        @Schema(description = "팀원 목록") List<TeamMemberResponse> members
) {
    public static TeamDetailResponse from(Team team, List<TeamMember> members) {
        return new TeamDetailResponse(
                team.getId(),
                team.getTeamName(),
                team.getTeamImage(),
                members.size(),
                team.getSuccessCount(),
                team.getConsecutiveTodoCount(),
                members.stream().map(TeamMemberResponse::from).toList()
        );
    }
}
