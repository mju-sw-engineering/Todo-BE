package com.todo.domain.notification.controller;

import com.todo.domain.notification.dto.response.NotificationPageResponse;
import com.todo.domain.notification.dto.response.UnreadNotificationCountResponse;
import com.todo.domain.notification.service.NotificationService;
import com.todo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController implements NotificationControllerDocs {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getNotifications(
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        NotificationPageResponse response = notificationService.getNotifications(authentication.getName(), cursorId, size);
        return ResponseEntity.ok(ApiResponse.success(response, "알림 목록을 조회했습니다"));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication
    ) {
        notificationService.markAsRead(authentication.getName(), notificationId);
        return ResponseEntity.ok(ApiResponse.success(null, "알림을 읽음 처리했습니다"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "모든 알림을 읽음 처리했습니다"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getUnreadCount(Authentication authentication) {
        UnreadNotificationCountResponse response = notificationService.getUnreadCount(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "미읽음 알림 수를 조회했습니다"));
    }
}
