package com.todo.domain.chat.dto.response;

public record ChatUnreadCountResponse(
        Long teamId,
        long unreadCount
) {
    public static ChatUnreadCountResponse of(Long teamId, long unreadCount) {
        return new ChatUnreadCountResponse(teamId, unreadCount);
    }
}
