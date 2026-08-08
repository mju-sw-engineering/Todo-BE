package com.todo.domain.notification.controller;

import com.todo.domain.notification.dto.response.NotificationPageResponse;
import com.todo.domain.notification.dto.response.UnreadNotificationCountResponse;
import com.todo.domain.notification.service.NotificationService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Test
    void 알림_목록을_반환한다() {
        NotificationController controller = new NotificationController(notificationService);
        NotificationPageResponse serviceResponse = new NotificationPageResponse(List.of(), false, null);
        given(notificationService.getNotifications("user1", null, 20)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<NotificationPageResponse>> response =
                controller.getNotifications(null, 20, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("알림 목록을 조회했습니다");
    }

    @Test
    void 알림_읽음_처리_응답을_반환한다() {
        NotificationController controller = new NotificationController(notificationService);

        ResponseEntity<ApiResponse<Void>> response = controller.markAsRead(1L, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("알림을 읽음 처리했습니다");
        then(notificationService).should().markAsRead("user1", 1L);
    }

    @Test
    void 전체_알림_읽음_처리_응답을_반환한다() {
        NotificationController controller = new NotificationController(notificationService);

        ResponseEntity<ApiResponse<Void>> response = controller.markAllAsRead(auth());

        assertThat(response.getBody().getMessage()).isEqualTo("모든 알림을 읽음 처리했습니다");
        then(notificationService).should().markAllAsRead("user1");
    }

    @Test
    void 미읽음_알림_수를_반환한다() {
        NotificationController controller = new NotificationController(notificationService);
        UnreadNotificationCountResponse serviceResponse = new UnreadNotificationCountResponse(3L);
        given(notificationService.getUnreadCount("user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> response =
                controller.getUnreadCount(auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("미읽음 알림 수를 조회했습니다");
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
