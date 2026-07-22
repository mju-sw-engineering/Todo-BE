package com.todo.domain.notification.event;

import com.todo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장 트랜잭션이 커밋된 뒤에만 WebSocket으로 발송한다.
 * 롤백된 트랜잭션의 알림이 클라이언트에 노출되는 것을 막는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationDispatch(NotificationDispatchEvent event) {
        notificationService.dispatch(event.deliveries());
    }
}
