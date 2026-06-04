package com.todo.domain.chat.dto.response;

public record ChatUnreadCountResponse(
        Long todoId,
        long unreadCount
) {
    public static ChatUnreadCountResponse of(Long todoId, long unreadCount) {
        return new ChatUnreadCountResponse(todoId, unreadCount);
    }
}
