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

    /**
     * DB에 저장하지 않고 WebSocket으로만 내보내는 알림. 저장된 행이 없으므로
     * notificationId는 null이고 읽음 처리 대상이 아니다.
     */
    public static NotificationResponse pushOnly(NotificationType type, String title, String content, Long referenceId) {
        return new NotificationResponse(
                null,
                type,
                title,
                content,
                false,
                referenceId,
                OffsetDateTime.now(ZoneOffset.ofHours(9))
        );
    }
}
