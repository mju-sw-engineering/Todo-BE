package com.todo.domain.notification.dto.response;

import com.todo.domain.notification.entity.Notification;
import com.todo.domain.notification.entity.NotificationType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String content,
        boolean isRead,
        Long referenceId,
        OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getReferenceId(),
                notification.getCreatedAt().atOffset(ZoneOffset.ofHours(9))
        );
    }
}
