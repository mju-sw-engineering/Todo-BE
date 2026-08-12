package com.todo.domain.todo.scheduler;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.todo.repository.TodoWorkItemNotificationInfo;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 마감 30분 전인 진행 중 WorkItem의 담당자에게 TODO_DEADLINE_APPROACHING을 1회 발송한다.
 * 이미 보낸 항목은 {@code deadlineReminderSentAt}으로 걸러 tick마다 중복 발송하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TodoDeadlineApproachingScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long REMINDER_LEAD_MINUTES = 30;

    private final TodoWorkItemRepository todoWorkItemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;

    @Scheduled(fixedDelayString = "${todo.scheduling.deadline-reminder-interval-ms:60000}")
    @Transactional
    public void notifyApproachingDeadlines() {
        LocalDateTime now = LocalDateTime.now(KST);
        List<TodoWorkItemNotificationInfo> approaching =
                todoWorkItemRepository.findApproachingDeadlineWorkItems(now, now.plusMinutes(REMINDER_LEAD_MINUTES));
        if (approaching.isEmpty()) {
            return;
        }

        List<Long> workItemIds = approaching.stream().map(TodoWorkItemNotificationInfo::getWorkItemId).toList();
        todoWorkItemRepository.markDeadlineReminderSent(workItemIds, now);

        notifyApproaching(approaching);
    }

    private void notifyApproaching(List<TodoWorkItemNotificationInfo> approaching) {
        List<Long> assigneeIds = approaching.stream()
                .map(TodoWorkItemNotificationInfo::getAssigneeId)
                .distinct()
                .toList();
        Map<Long, User> assignees = userRepository.findAllById(assigneeIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        for (TodoWorkItemNotificationInfo item : approaching) {
            User assignee = assignees.get(item.getAssigneeId());
            if (assignee == null) {
                continue;
            }
            notificationService.send(
                    assignee,
                    null,
                    notificationMessageFactory.todoDeadlineApproaching(item.getTodoTitle()),
                    item.getTodoId()
            );
        }
    }
}
