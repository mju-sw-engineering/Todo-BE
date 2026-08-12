package com.todo.domain.todo.service;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Todo 상태 전이를 한 곳에서 판정한다.
 *
 * <p>제출·이탈·구성 변경 세 경로가 같은 전이 규칙과 같은 카운터 증가 규칙을 공유해야 한다.
 * 경로마다 따로 구현하면 같은 Todo가 어떤 경로를 거쳤느냐에 따라 다른 상태로 끝난다.
 *
 * <p>모든 메서드는 호출자가 부모 Todo를 비관적 락으로 잠근 뒤 같은 트랜잭션에서 부르는 것을 전제한다.
 * 잠그지 않고 부르면 동시 제출·제거 사이에서 상태와 팀 카운터가 어긋난다.
 *
 * <p>알림 발송도 상태 판정과 같은 곳에 둔다. 판정 결과(전이가 실제로 일어났는지)를 호출부가
 * 다시 해석해서 알림을 따로 쏘게 하면, 상태를 "한 곳에서 판정"한다는 이 클래스의 목적과
 * 어긋나게 판정 로직이 흩어진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoStatusTransitionService {

    private final TodoWorkItemRepository todoWorkItemRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;

    /**
     * 남은 WorkItem 구성으로 부모 Todo 상태를 재평가한다.
     *
     * <p>판정 순서는 실행 항목 없음 → 실패 항목 존재 → 전원 성공이다.
     * 실패가 하나라도 있으면 남은 항목의 성공 여부와 무관하게 Todo는 실패로 확정한다.
     * 팀 성공 카운터는 {@code IN_PROGRESS -> SUCCESS} 전이가 실제로 일어난 경우에만 1 증가한다.
     */
    @Transactional
    public void reevaluate(Todo todo) {
        long totalCount = todoWorkItemRepository.countByTodoId(todo.getId());
        if (totalCount == 0) {
            todo.markAsFail();
            return;
        }
        long failCount = todoWorkItemRepository.countByTodoIdAndStatus(todo.getId(), WorkItemStatus.FAIL);
        if (failCount > 0) {
            todo.markAsFail();
            return;
        }
        long successCount = todoWorkItemRepository.countByTodoIdAndStatus(todo.getId(), WorkItemStatus.SUCCESS);
        if (successCount == totalCount && todo.markAsSuccess()) {
            teamRepository.incrementSuccessCount(todo.getTeam().getId());
            notifyAllCompleted(todo);
        }
    }

    private void notifyAllCompleted(Todo todo) {
        List<User> receivers = teamMemberRepository.findByTeamIdWithUser(todo.getTeam().getId()).stream()
                .map(TeamMember::getUser)
                .toList();
        notificationService.sendAll(
                receivers,
                null,
                notificationMessageFactory.todoAllCompleted(todo.getTitle()),
                todo.getId()
        );
    }

    /**
     * 마감이 지난 것으로 확인된 WorkItem과 부모 Todo를 함께 실패로 확정한다.
     *
     * <p>제출과 재배정이 락 획득 후 마감 초과를 발견했을 때 쓴다.
     * 스케줄러의 일괄 만료와 달리 이미 잠근 단일 항목만 다룬다.
     */
    @Transactional
    public void failOnDeadlinePassed(Todo todo, TodoWorkItem workItem) {
        workItem.markAsFail();
        todo.markAsFail();
        if (workItem.getAssignee() != null) {
            notificationService.send(
                    workItem.getAssignee(),
                    null,
                    notificationMessageFactory.todoWorkItemExpired(todo.getTitle()),
                    todo.getId()
            );
        }
    }
}
