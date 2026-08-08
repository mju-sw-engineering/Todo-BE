package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TeamDetailResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "팀 이름") String teamName,
        @Schema(description = "팀 설명") String description,
        @Schema(description = "팀 이미지 URL") String teamImageUrl,
        @Schema(description = "초대 코드") String inviteCode,
        @Schema(description = "팀원 수") int memberCount,
        @Schema(description = "성공 개수") int successCount,
        @Schema(description = "팀원 목록") List<TeamMemberResponse> members
) {
    public static TeamDetailResponse from(Team team, List<TeamMemberResponse> members) {
        return new TeamDetailResponse(
                team.getId(),
                team.getTeamName(),
                team.getDescription(),
                team.getTeamImage(),
                team.getInviteCode(),
                members.size(),
                team.getSuccessCount(),
                members
        );
    }

    public TeamDetailResponse withImageUrl(String resolvedImageUrl) {
        return new TeamDetailResponse(
                teamId, teamName, description, resolvedImageUrl, inviteCode, memberCount, successCount, members);
    }
}
