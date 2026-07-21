package com.todo.domain.auth.dto.response;

import com.todo.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignupResponse(
        @Schema(description = "사용자 ID") Long id,
        @Schema(description = "로그인 아이디") String loginId,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getLoginId(), user.getNickname(), user.getProfileImageUrl());
    }

    public SignupResponse withImageUrl(String resolvedImageUrl) {
        return new SignupResponse(id, loginId, nickname, resolvedImageUrl);
    }
}
