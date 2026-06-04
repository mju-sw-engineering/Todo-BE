package com.todo.domain.notification.controller;

import com.todo.domain.notification.dto.response.NotificationPageResponse;
import com.todo.domain.notification.dto.response.UnreadNotificationCountResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Notification", description = "알림 API")
public interface NotificationControllerDocs {

    @Operation(
            summary = "알림 목록 조회",
            description = "커서 기반으로 알림 목록을 조회합니다. cursorId 없으면 최신 알림부터 반환합니다."
    )
    ResponseEntity<ApiResponse<NotificationPageResponse>> getNotifications(
            @Parameter(description = "커서 ID") Long cursorId,
            @Parameter(description = "조회 개수 (기본값: 20)") int size,
            Authentication authentication
    );

    @Operation(
            summary = "알림 단건 읽음 처리",
            description = "특정 알림을 읽음 처리합니다."
    )
    ResponseEntity<ApiResponse<Void>> markAsRead(
            @Parameter(description = "알림 ID") Long notificationId,
            Authentication authentication
    );

    @Operation(
            summary = "알림 전체 읽음 처리",
            description = "본인의 모든 알림을 읽음 처리합니다."
    )
    ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication);

    @Operation(
            summary = "안 읽은 알림 수 조회",
            description = "읽지 않은 알림 수를 반환합니다."
    )
    ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getUnreadCount(Authentication authentication);
}
