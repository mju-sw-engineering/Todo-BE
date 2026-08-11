package com.todo.domain.user.dto.response;

import com.todo.domain.auth.entity.AuthProvider;
import com.todo.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 프로필 응답 (팀 목록 제외한 경량 응답)")
public record UserProfileResponse(
        @Schema(description = "사용자 ID") Long userId,
        @Schema(description = "로그인 아이디 (소셜 계정은 null)") String loginId,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "가입 경로. 탈퇴 재인증 방식이 이 값에 따라 갈린다") AuthProvider provider
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getLoginId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider()
        );
    }

    public UserProfileResponse withProfileImageUrl(String resolvedProfileImageUrl) {
        return new UserProfileResponse(userId, loginId, nickname, resolvedProfileImageUrl, provider);
    }
}
