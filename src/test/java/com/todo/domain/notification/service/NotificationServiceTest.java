package com.todo.domain.notification.service;

import com.todo.domain.notification.dto.response.NotificationResponse;
import com.todo.domain.notification.entity.Notification;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.event.NotificationDelivery;
import com.todo.domain.notification.event.NotificationDispatchEvent;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션_안에서는_저장만_하고_발송은_커밋_후로_미룬다() {
        TransactionSynchronizationManager.initSynchronization();
        User receiver = userWithId(2L, "user2");
        givenSavedNotifications();

        notificationService.sendAll(List.of(receiver), null, new NotificationMessage(NotificationType.TODO_CREATED, "제목", "내용"), 10L);

        then(messagingTemplate).should(never()).convertAndSendToUser(anyString(), anyString(), any());
        ArgumentCaptor<NotificationDispatchEvent> captor = ArgumentCaptor.forClass(NotificationDispatchEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().deliveries())
                .extracting(NotificationDelivery::receiverLoginId)
                .containsExactly("user2");
    }

    @Test
    void 트랜잭션이_없으면_즉시_발송한다() {
        User receiver = userWithId(2L, "user2");
        givenSavedNotifications();

        notificationService.sendAll(List.of(receiver), null, new NotificationMessage(NotificationType.TODO_CREATED, "제목", "내용"), 10L);

        then(eventPublisher).should(never()).publishEvent(any(NotificationDispatchEvent.class));
        then(messagingTemplate).should()
                .convertAndSendToUser(eq("user2"), eq("/queue/notifications"), any(NotificationResponse.class));
    }

    @Test
    void 여러_수신자에게_한_번의_saveAll로_저장한다() {
        List<User> receivers = List.of(userWithId(2L, "user2"), userWithId(3L, "user3"));
        givenSavedNotifications();

        notificationService.sendAll(receivers, null, new NotificationMessage(NotificationType.CHAT_MESSAGE, "제목", "내용"), 10L);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        then(notificationRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        then(messagingTemplate).should(times(2))
                .convertAndSendToUser(anyString(), anyString(), any(NotificationResponse.class));
    }

    @Test
    void 수신자가_없으면_저장도_발송도_하지_않는다() {
        notificationService.sendAll(List.of(), null, new NotificationMessage(NotificationType.TODO_CREATED, "제목", "내용"), 10L);

        then(notificationRepository).should(never()).saveAll(any());
        then(messagingTemplate).should(never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void WebSocket_발송이_실패해도_예외를_전파하지_않는다() {
        willThrow(new RuntimeException("boom")).given(messagingTemplate)
                .convertAndSendToUser(anyString(), anyString(), any(NotificationResponse.class));
        User receiver = userWithId(2L, "user2");
        givenSavedNotifications();

        assertThatCode(() -> notificationService.sendAll(
                List.of(receiver), null, new NotificationMessage(NotificationType.TODO_CREATED, "제목", "내용"), 10L))
                .doesNotThrowAnyException();
    }

    @Test
    void pushAll은_저장하지_않고_WebSocket으로만_발송한다() {
        List<User> receivers = List.of(userWithId(2L, "user2"), userWithId(3L, "user3"));

        notificationService.pushAll(receivers, new NotificationMessage(NotificationType.CHAT_MESSAGE, "제목", "내용"), 100L);

        then(notificationRepository).should(never()).saveAll(any());
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);
        then(messagingTemplate).should(times(2))
                .convertAndSendToUser(anyString(), eq("/queue/notifications"), captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(payload -> {
            assertThat(payload.notificationId()).isNull();
            assertThat(payload.type()).isEqualTo(NotificationType.CHAT_MESSAGE);
            assertThat(payload.referenceId()).isEqualTo(100L);
            assertThat(payload.isRead()).isFalse();
        });
    }

    @Test
    void pushAll도_트랜잭션_안에서는_발송을_커밋_후로_미룬다() {
        TransactionSynchronizationManager.initSynchronization();
        User receiver = userWithId(2L, "user2");

        notificationService.pushAll(List.of(receiver), new NotificationMessage(NotificationType.CHAT_MESSAGE, "제목", "내용"), 100L);

        then(messagingTemplate).should(never()).convertAndSendToUser(anyString(), anyString(), any());
        ArgumentCaptor<NotificationDispatchEvent> captor = ArgumentCaptor.forClass(NotificationDispatchEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().deliveries())
                .extracting(NotificationDelivery::receiverLoginId)
                .containsExactly("user2");
    }

    @Test
    void pushAll은_수신자가_없으면_발송하지_않는다() {
        notificationService.pushAll(List.of(), new NotificationMessage(NotificationType.CHAT_MESSAGE, "제목", "내용"), 100L);

        then(messagingTemplate).should(never()).convertAndSendToUser(anyString(), anyString(), any());
        then(eventPublisher).should(never()).publishEvent(any(NotificationDispatchEvent.class));
    }

    private void givenSavedNotifications() {
        AtomicLong sequence = new AtomicLong(1L);
        given(notificationRepository.saveAll(any())).willAnswer(invocation -> {
            List<Notification> notifications = invocation.getArgument(0);
            notifications.forEach(notification -> {
                ReflectionTestUtils.setField(notification, "id", sequence.getAndIncrement());
                ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 7, 22, 12, 0));
            });
            return notifications;
        });
    }

    private User userWithId(Long id, String loginId) {
        User user = User.create(loginId, "password", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
