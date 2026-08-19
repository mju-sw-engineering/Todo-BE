package com.todo.domain.auth.event;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code SessionService}가 {@link NotificationService}를 직접 의존하면 순환 참조가 생긴다:
 * {@code NotificationService}는 {@code SimpMessagingTemplate}을 거쳐 {@code WebSocketConfig} →
 * {@code WebSocketAuthChannelInterceptor} → {@code AuthService} → {@code SessionService}로
 * 이어지는 빈 그래프에 물려 있다. 이벤트로 한 단계 끊어 이 경로를 피한다.
 */
@Component
@RequiredArgsConstructor
public class NewDeviceLoginEventListener {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewDeviceLoginDetected(NewDeviceLoginDetectedEvent event) {
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            return;
        }
        notificationService.send(user, null, notificationMessageFactory.newDeviceLogin(), null, null);
    }
}
