package com.todo.domain.todo.service;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoWorkItemLifecycleService {

    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final TodoReactionRepository todoReactionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;
    private final TodoStatusTransitionService todoStatusTransitionService;

    /**
     * 강퇴, 팀 나가기, 회원 탈퇴에서 공통으로 쓰는 진행 중 배정 정리 정책이다.
     */
    @Transactional
    public void handleTeamDeparture(Long teamId, User departingUser) {
        List<TodoWorkItem> snapshots = todoWorkItemRepository.findInProgressByTeamIdAndAssigneeId(
                teamId,
                departingUser.getId()
        );
        Map<Long, List<TodoWorkItem>> byTodoId = snapshots.stream()
                .collect(Collectors.groupingBy(
                        workItem -> workItem.getTodo().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<Long, List<TodoWorkItem>> entry : byTodoId.entrySet()) {
            handleTodoDeparture(entry.getKey(), entry.getValue(), departingUser);
        }
    }

    /**
     * 회원 탈퇴에서 완료·실패 기록은 성과 이력을 남기되 담당자와 사진 키만 익명화한다.
     */
    @Transactional
    public void anonymizeFinishedForWithdrawal(Long userId) {
        todoWorkItemRepository.anonymizeFinishedByAssigneeId(userId);
    }

    private void handleTodoDeparture(Long todoId, List<TodoWorkItem> snapshots, User departingUser) {
        Todo todo = todoRepository.findByIdWithLock(todoId).orElse(null);
        if (todo == null) {
            return;
        }

        Map<Long, TodoWorkItem> lockedItems = snapshots.stream()
                .sorted(Comparator.comparing(TodoWorkItem::getId))
                .map(snapshot -> todoWorkItemRepository.findByIdWithLock(snapshot.getId()).orElse(null))
                .filter(workItem -> workItem != null)
                .filter(workItem -> workItem.getStatus() == WorkItemStatus.IN_PROGRESS)
                .filter(workItem -> workItem.getAssignee() != null)
                .filter(workItem -> departingUser.getId().equals(workItem.getAssignee().getId()))
                .collect(Collectors.toMap(
                        TodoWorkItem::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (lockedItems.isEmpty()) {
            return;
        }

        List<TodoWorkItem> allWorkItems = todoWorkItemRepository.findByTodoIdOrderByPositionAsc(todoId);
        boolean anotherDirectAssigneeRemains = allWorkItems.stream()
                .filter(workItem -> !lockedItems.containsKey(workItem.getId()))
                .filter(workItem -> workItem.getType() == WorkItemType.DIRECT)
                .anyMatch(workItem -> workItem.getAssignee() != null);

        int unassignedCount = 0;
        for (TodoWorkItem workItem : lockedItems.values()) {
            if (workItem.getType() == WorkItemType.DIRECT && anotherDirectAssigneeRemains) {
                todoReactionRepository.deleteByTodoWorkItemIdIn(List.of(workItem.getId()));
                todoWorkItemRepository.delete(workItem);
            } else {
                workItem.unassign();
                unassignedCount++;
            }
        }

        todoStatusTransitionService.reevaluate(todo);
        if (unassignedCount > 0) {
            sendUnassignedNotification(todo, departingUser, unassignedCount);
        }
    }

    private void sendUnassignedNotification(Todo todo, User departingUser, int unassignedCount) {
        List<User> receivers = teamMemberRepository.findByTeamIdExcludingUser(
                        todo.getTeam().getId(),
                        departingUser.getId()
                ).stream()
                .map(TeamMember::getUser)
                .toList();
        notificationService.sendAll(
                receivers,
                departingUser,
                notificationMessageFactory.todoUnassigned(todo.getTitle(), unassignedCount),
                todo.getId(),
                todo.getTeam().getId()
        );
    }
}
