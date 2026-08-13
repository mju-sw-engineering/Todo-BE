package com.todo.domain.todo.scheduler;

import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.todo.repository.TodoWorkItemNotificationInfo;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TodoDeadlineApproachingSchedulerTest {

    @InjectMocks
    private TodoDeadlineApproachingScheduler scheduler;

    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        given(todoWorkItemRepository.findApproachingDeadlineWorkItems(any(), any())).willReturn(List.of());

        scheduler.notifyApproachingDeadlines();

        then(todoWorkItemRepository).should(never()).markDeadlineReminderSent(any(), any());
        then(notificationService).should(never()).send(any(), any(), any(), any());
    }

    @Test
    void 마감_임박_WorkItem의_담당자에게_알림을_보내고_리마인더_발송_표시를_남긴다() {
        TodoWorkItemNotificationInfo info = Mockito.mock(TodoWorkItemNotificationInfo.class);
        given(info.getWorkItemId()).willReturn(30L);
        given(info.getTodoId()).willReturn(10L);
        given(info.getTodoTitle()).willReturn("기말 발표");
        given(info.getAssigneeId()).willReturn(1L);
        given(todoWorkItemRepository.findApproachingDeadlineWorkItems(any(), any())).willReturn(List.of(info));
        User assignee = User.create("1", "pw", "닉네임", null);
        ReflectionTestUtils.setField(assignee, "id", 1L);
        given(userRepository.findAllById(List.of(1L))).willReturn(List.of(assignee));
        NotificationMessage message = new NotificationMessage(NotificationType.TODO_DEADLINE_APPROACHING, "title", "content");
        given(notificationMessageFactory.todoDeadlineApproaching("기말 발표")).willReturn(message);

        scheduler.notifyApproachingDeadlines();

        then(todoWorkItemRepository).should().markDeadlineReminderSent(eq(List.of(30L)), any(LocalDateTime.class));
        then(notificationService).should().send(assignee, null, message, 10L);
    }

    @Test
    void 담당자를_찾을_수_없으면_알림만_건너뛰고_리마인더_표시는_남긴다() {
        TodoWorkItemNotificationInfo info = Mockito.mock(TodoWorkItemNotificationInfo.class);
        given(info.getWorkItemId()).willReturn(30L);
        given(info.getAssigneeId()).willReturn(99L);
        given(todoWorkItemRepository.findApproachingDeadlineWorkItems(any(), any())).willReturn(List.of(info));
        given(userRepository.findAllById(List.of(99L))).willReturn(List.of());

        scheduler.notifyApproachingDeadlines();

        then(todoWorkItemRepository).should().markDeadlineReminderSent(eq(List.of(30L)), any(LocalDateTime.class));
        then(notificationService).should(never()).send(any(), any(), any(), any());
    }
}
