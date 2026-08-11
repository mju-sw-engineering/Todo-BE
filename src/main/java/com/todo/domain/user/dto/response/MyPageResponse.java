package com.todo.domain.user.dto.response;

import com.todo.domain.auth.entity.AuthProvider;
import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "마이페이지 응답")
public record MyPageResponse(
        @Schema(description = "사용자 ID") Long userId,
        @Schema(description = "로그인 아이디 (소셜 계정은 null)") String loginId,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "가입 경로. 탈퇴 재인증 방식이 이 값에 따라 갈린다") AuthProvider provider,
        @Schema(description = "소속 팀 목록") List<TeamSummaryResponse> teams
) {
    public static MyPageResponse from(User user) {
        return new MyPageResponse(
                user.getId(),
                user.getLoginId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                List.of()
        );
    }

    public MyPageResponse withProfileImageUrl(String resolvedProfileImageUrl) {
        return new MyPageResponse(userId, loginId, nickname, resolvedProfileImageUrl, provider, teams);
    }

    public MyPageResponse withTeams(List<TeamSummaryResponse> teams) {
        return new MyPageResponse(userId, loginId, nickname, profileImageUrl, provider, teams);
    }
}
