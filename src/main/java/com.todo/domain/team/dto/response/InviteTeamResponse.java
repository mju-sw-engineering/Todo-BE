package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "팀 이메일 초대 응답")
public record InviteTeamResponse(
        @Schema(description = "팀 ID")
        Long teamId,

        @Schema(description = "팀 이름")
        String teamName,

        @Schema(description = "초대 코드")
        String inviteCode,

        @Schema(description = "초대 링크")
        String inviteLink,

        @Schema(description = "발송된 이메일 수")
        int sentCount,

        @Schema(description = "발송된 이메일 목록")
        List<String> emails
) {
    public static InviteTeamResponse from(Team team, String inviteLink, List<String> emails) {
        return new InviteTeamResponse(
                team.getId(),
                team.getTeamName(),
                team.getInviteCode(),
                inviteLink,
                emails.size(),
                emails
        );
    }
}
