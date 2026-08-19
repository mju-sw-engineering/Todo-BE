package com.todo.domain.notification.event;

import com.todo.domain.notification.dto.response.NotificationResponse;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.entity.ReferenceType;
import com.todo.domain.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Mock
    private NotificationService notificationService;

    @Test
    void 커밋_후_이벤트를_받으면_WebSocket_발송을_위임한다() {
        List<NotificationDelivery> deliveries = List.of(new NotificationDelivery(
                "user2",
                new NotificationResponse(
                        1L, NotificationType.TODO_CREATED, ReferenceType.TODO, "제목", "내용",
                        false, 10L, 20L, OffsetDateTime.now())
        ));

        notificationEventListener.onNotificationDispatch(new NotificationDispatchEvent(deliveries));

        then(notificationService).should().dispatch(deliveries);
    }
}
