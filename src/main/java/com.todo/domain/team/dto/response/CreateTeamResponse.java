package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CreateTeamResponse(
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "팀 이름") String teamName,
        @Schema(description = "팀 이미지 URL") String teamImage,
        @Schema(description = "초대 코드") String inviteCode,
        @Schema(description = "팀장 ID") Long leaderId,
        @Schema(description = "연속 todo 횟수") int consecutiveTodoCount,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {
    public static CreateTeamResponse from(Team team, Long leaderId) {
        return new CreateTeamResponse(
                team.getId(),
                team.getTeamName(),
                team.getTeamImage(),
                team.getInviteCode(),
                leaderId,
                team.getConsecutiveTodoCount(),
                team.getCreatedAt()
        );
    }
}
