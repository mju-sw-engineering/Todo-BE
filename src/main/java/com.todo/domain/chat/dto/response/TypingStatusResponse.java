package com.todo.domain.chat.dto.response;

import com.todo.domain.user.entity.User;

public record TypingStatusResponse(
        Long userId,
        String nickname,
        boolean isTyping
) {
    public static TypingStatusResponse of(User user, boolean isTyping) {
        return new TypingStatusResponse(user.getId(), user.getNickname(), isTyping);
    }
}
