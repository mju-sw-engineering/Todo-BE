package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CreateTeamResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "팀 이름") String teamName,
        @Schema(description = "팀 이미지 URL") String teamImage,
        @Schema(description = "초대 코드") String inviteCode,
        @Schema(description = "팀장 ID") Long leaderId,
        @Schema(description = "생성 시각", example = "2026-05-21T12:34:56+09:00") OffsetDateTime createdAt
) {
    public static CreateTeamResponse from(Team team, Long leaderId) {
        return new CreateTeamResponse(
                team.getId(),
                team.getTeamName(),
                team.getTeamImage(),
                team.getInviteCode(),
                leaderId,
                toKstOffset(team.getCreatedAt())
        );
    }

    public CreateTeamResponse withImageUrl(String resolvedImageUrl) {
        return new CreateTeamResponse(teamId, teamName, resolvedImageUrl, inviteCode, leaderId, createdAt);
    }

    private static OffsetDateTime toKstOffset(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.atOffset(ZoneOffset.ofHours(9));
    }
}
