package com.todo.domain.auth.dto.response;

import com.todo.domain.user.entity.User;

public record SignupResponse(
        Long id,
        String loginId,
        String nickname,
        String profileImageUrl
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getLoginId(), user.getNickname(), user.getProfileImageUrl());
    }
}
