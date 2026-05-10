package com.todo.domain.team.dto.response;

import com.todo.domain.team.entity.TeamMember;
import io.swagger.v3.oas.annotations.media.Schema;

public record TeamMemberResponse(
        @Schema(description = "사용자 ID") Long userId,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "역할 (LEADER / MEMBER)") String role
) {
    public static TeamMemberResponse from(TeamMember teamMember) {
        return new TeamMemberResponse(
                teamMember.getUser().getId(),
                teamMember.getUser().getNickname(),
                teamMember.getUser().getProfileImageUrl(),
                teamMember.getRole().name()
        );
    }
}
