package com.todo.domain.todo.scheduler;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.todo.repository.TodoRepository;
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
 * 마감 시간이 지난 WorkItem과 부모 Todo를 주기적으로 FAIL 처리한다.
 * 기존에는 조회 경로에서 매번 갱신했으나, 조회를 읽기 전용으로 유지하기 위해 스케줄러로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class TodoExpiryScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;

    @Scheduled(fixedDelayString = "${todo.scheduling.expiry-interval-ms:60000}")
    @Transactional
    public void expireOverdueTodos() {
        LocalDateTime now = LocalDateTime.now(KST);
        List<Long> overdueTodoIds = todoWorkItemRepository.findOverdueTodoIds(now);
        if (overdueTodoIds.isEmpty()) {
            return;
        }

        // 벌크 UPDATE(clearAutomatically)가 영속성 컨텍스트를 비우기 전에 알림에 필요한
        // 담당자·투두 정보를 먼저 읽어둔다. 이후에는 이 스냅샷만 쓰고 관리되던 엔티티
        // 참조는 다시 조회한다.
        List<TodoWorkItemNotificationInfo> expiring = todoWorkItemRepository.findOverdueForNotification(now);

        todoWorkItemRepository.markOverdueAsFail(now);
        todoRepository.markAsFailByIds(overdueTodoIds);

        notifyExpired(expiring);
    }

    private void notifyExpired(List<TodoWorkItemNotificationInfo> expiring) {
        if (expiring.isEmpty()) {
            return;
        }
        List<Long> assigneeIds = expiring.stream().map(TodoWorkItemNotificationInfo::getAssigneeId).distinct().toList();
        Map<Long, User> assignees = userRepository.findAllById(assigneeIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        for (TodoWorkItemNotificationInfo item : expiring) {
            User assignee = assignees.get(item.getAssigneeId());
            if (assignee == null) {
                continue;
            }
            notificationService.send(
                    assignee,
                    null,
                    notificationMessageFactory.todoWorkItemExpired(item.getTodoTitle()),
                    item.getTodoId(),
                    item.getTeamId()
            );
        }
    }
}
