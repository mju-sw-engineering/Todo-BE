package com.todo.domain.auth.event;

import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NewDeviceLoginEventListenerTest {

    @InjectMocks
    private NewDeviceLoginEventListener listener;

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create("localUser", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void 사용자가_존재하면_새기기_로그인_알림을_보낸다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        NotificationMessage message = new NotificationMessage(NotificationType.NEW_DEVICE_LOGIN, "title", "content");
        given(notificationMessageFactory.newDeviceLogin()).willReturn(message);

        listener.onNewDeviceLoginDetected(new NewDeviceLoginDetectedEvent(1L));

        verify(notificationService).send(user, null, message, null);
    }

    @Test
    void 탈퇴등으로_사용자가_없으면_알림을_보내지_않는다() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        listener.onNewDeviceLoginDetected(new NewDeviceLoginDetectedEvent(1L));

        verify(notificationService, never()).send(any(), any(), any(), any());
    }
}
