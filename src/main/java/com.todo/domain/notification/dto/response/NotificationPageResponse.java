package com.todo.domain.notification.dto.response;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> notifications,
        boolean hasNext,
        Long nextCursorId
) {}
