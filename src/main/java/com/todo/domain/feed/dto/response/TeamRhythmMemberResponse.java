package com.todo.domain.feed.dto.response;

import com.todo.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record TeamRhythmMemberResponse(
        @Schema(description = "사용자 ID") Long userId,
        @Schema(description = "닉네임") String name
) {
    public static TeamRhythmMemberResponse from(User user) {
        return new TeamRhythmMemberResponse(user.getId(), user.getNickname());
    }
}
