package com.todo.domain.notification.dto.response;

public record UnreadNotificationCountResponse(long unreadCount) {
    public static UnreadNotificationCountResponse of(long unreadCount) {
        return new UnreadNotificationCountResponse(unreadCount);
    }
}
