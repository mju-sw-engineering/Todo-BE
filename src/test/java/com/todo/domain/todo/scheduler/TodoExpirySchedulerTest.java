package com.todo.domain.todo.scheduler;

import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemNotificationInfo;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TodoExpirySchedulerTest {

    @InjectMocks
    private TodoExpiryScheduler todoExpiryScheduler;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    @Test
    void 유효_마감이_지난_WorkItem의_부모_Todo만_FAIL_처리하고_다른_항목은_유지한다() {
        given(todoWorkItemRepository.findOverdueTodoIds(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .willReturn(List.of(10L));

        todoExpiryScheduler.expireOverdueTodos();

        ArgumentCaptor<LocalDateTime> overdueLookupNowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> workItemNowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(todoWorkItemRepository).should().findOverdueTodoIds(overdueLookupNowCaptor.capture());
        then(todoWorkItemRepository).should().markOverdueAsFail(workItemNowCaptor.capture());
        then(todoRepository).should().markAsFailByIds(List.of(10L));

        assertThat(overdueLookupNowCaptor.getValue()).isNotNull();
        assertThat(workItemNowCaptor.getValue()).isEqualTo(overdueLookupNowCaptor.getValue());
    }

    @Test
    void 만료된_WorkItem의_담당자에게_만료_알림을_보낸다() {
        given(todoWorkItemRepository.findOverdueTodoIds(any(LocalDateTime.class))).willReturn(List.of(10L));
        TodoWorkItemNotificationInfo info = org.mockito.Mockito.mock(TodoWorkItemNotificationInfo.class);
        given(info.getTodoId()).willReturn(10L);
        given(info.getTodoTitle()).willReturn("기말 발표");
        given(info.getAssigneeId()).willReturn(1L);
        given(info.getTeamId()).willReturn(20L);
        given(todoWorkItemRepository.findOverdueForNotification(any(LocalDateTime.class))).willReturn(List.of(info));
        User assignee = User.create("1", "pw", "닉네임", null);
        ReflectionTestUtils.setField(assignee, "id", 1L);
        given(userRepository.findAllById(List.of(1L))).willReturn(List.of(assignee));
        NotificationMessage message = new NotificationMessage(NotificationType.TODO_WORK_ITEM_EXPIRED, "title", "content");
        given(notificationMessageFactory.todoWorkItemExpired("기말 발표")).willReturn(message);

        todoExpiryScheduler.expireOverdueTodos();

        then(notificationService).should().send(assignee, null, message, 10L, 20L);
    }

    @Test
    void 담당자를_찾을_수_없으면_만료_알림을_건너뛴다() {
        given(todoWorkItemRepository.findOverdueTodoIds(any(LocalDateTime.class))).willReturn(List.of(10L));
        TodoWorkItemNotificationInfo info = org.mockito.Mockito.mock(TodoWorkItemNotificationInfo.class);
        given(info.getAssigneeId()).willReturn(99L);
        given(todoWorkItemRepository.findOverdueForNotification(any(LocalDateTime.class))).willReturn(List.of(info));
        given(userRepository.findAllById(List.of(99L))).willReturn(List.of());

        todoExpiryScheduler.expireOverdueTodos();

        then(notificationService).should(never()).send(any(), any(), any(), any(), any());
    }
}
