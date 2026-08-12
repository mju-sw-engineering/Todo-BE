package com.todo.domain.todo.service;

import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoWorkItemLifecycleServiceTest {

    private static final Long TEAM_ID = 10L;
    private static final Long TODO_ID = 20L;

    @InjectMocks
    private TodoWorkItemLifecycleService lifecycleService;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;
    @Mock
    private TodoReactionRepository todoReactionRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    @BeforeEach
    void setUp() {
        // 상태 전이는 목이 아니라 실제 구현을 주입한다.
        // 이탈 경로가 공통 서비스와 합쳐진 뒤에도 같은 상태·카운터 결과를 내는지가 이 테스트의 검증 대상이다.
        ReflectionTestUtils.setField(
                lifecycleService,
                "todoStatusTransitionService",
                new TodoStatusTransitionService(
                        todoWorkItemRepository, teamRepository, teamMemberRepository,
                        notificationService, notificationMessageFactory)
        );
    }

    @Test
    void 진행중_TASK_담당자가_이탈하면_업무를_미배정으로_보존하고_팀에_알린다() {
        User departing = user(1L);
        User remaining = user(2L);
        Team team = team();
        Todo todo = todo(team, TodoMode.TASK);
        TodoWorkItem task = task(todo, departing, 30L);
        NotificationMessage message = org.mockito.Mockito.mock(NotificationMessage.class);
        given(todoWorkItemRepository.findInProgressByTeamIdAndAssigneeId(TEAM_ID, 1L)).willReturn(List.of(task));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(30L)).willReturn(Optional.of(task));
        given(todoWorkItemRepository.findByTodoIdOrderByPositionAsc(TODO_ID)).willReturn(List.of(task));
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(1L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(0L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.SUCCESS)).willReturn(0L);
        given(teamMemberRepository.findByTeamIdExcludingUser(TEAM_ID, 1L))
                .willReturn(List.of(TeamMember.create(team, remaining, TeamMemberRole.MEMBER)));
        given(notificationMessageFactory.todoUnassigned("투두", 1)).willReturn(message);

        lifecycleService.handleTeamDeparture(TEAM_ID, departing);

        assertThat(task.getAssignee()).isNull();
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
        then(notificationService).should().sendAll(List.of(remaining), departing, message, TODO_ID);
    }

    @Test
    void DIRECT에_다른_담당자가_남으면_이탈자_행을_삭제하고_남은_성공으로_Todo를_완료한다() {
        User departing = user(1L);
        User remaining = user(2L);
        Team team = team();
        Todo todo = todo(team, TodoMode.DIRECT);
        TodoWorkItem leavingItem = direct(todo, departing, 30L);
        TodoWorkItem completedItem = direct(todo, remaining, 31L);
        completedItem.markAsSuccess();
        given(todoWorkItemRepository.findInProgressByTeamIdAndAssigneeId(TEAM_ID, 1L))
                .willReturn(List.of(leavingItem));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(30L)).willReturn(Optional.of(leavingItem));
        given(todoWorkItemRepository.findByTodoIdOrderByPositionAsc(TODO_ID))
                .willReturn(List.of(leavingItem, completedItem));
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(1L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(0L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.SUCCESS)).willReturn(1L);

        lifecycleService.handleTeamDeparture(TEAM_ID, departing);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
        then(todoReactionRepository).should().deleteByTodoWorkItemIdIn(List.of(30L));
        then(todoWorkItemRepository).should().delete(leavingItem);
        then(teamRepository).should().incrementSuccessCount(TEAM_ID);
        // Todo가 이 재평가로 SUCCESS 전이되므로 TODO_ALL_COMPLETED 알림이 나간다.
        then(notificationService).should().sendAll(any(), eq(null), any(), eq(TODO_ID));
    }

    @Test
    void 다른_WorkItem이_실패한_Todo는_이탈_재평가에서도_FAIL을_유지한다() {
        User departing = user(1L);
        Team team = team();
        Todo todo = todo(team, TodoMode.TASK);
        TodoWorkItem leavingTask = task(todo, departing, 30L);
        TodoWorkItem failedTask = task(todo, user(2L), 31L);
        failedTask.markAsFail();
        NotificationMessage message = org.mockito.Mockito.mock(NotificationMessage.class);
        given(todoWorkItemRepository.findInProgressByTeamIdAndAssigneeId(TEAM_ID, 1L))
                .willReturn(List.of(leavingTask));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.of(todo));
        given(todoWorkItemRepository.findByIdWithLock(30L)).willReturn(Optional.of(leavingTask));
        given(todoWorkItemRepository.findByTodoIdOrderByPositionAsc(TODO_ID))
                .willReturn(List.of(leavingTask, failedTask));
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(2L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(1L);
        given(teamMemberRepository.findByTeamIdExcludingUser(TEAM_ID, 1L)).willReturn(List.of());
        given(notificationMessageFactory.todoUnassigned("투두", 1)).willReturn(message);

        lifecycleService.handleTeamDeparture(TEAM_ID, departing);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.FAIL);
        then(teamRepository).shouldHaveNoInteractions();
        then(notificationService).should().sendAll(List.of(), departing, message, TODO_ID);
    }

    @Test
    void 삭제된_Todo나_잠금_시점에_사라진_WorkItem은_조용히_건너뛴다() {
        User departing = user(1L);
        Todo todo = todo(team(), TodoMode.TASK);
        TodoWorkItem task = task(todo, departing, 30L);
        given(todoWorkItemRepository.findInProgressByTeamIdAndAssigneeId(TEAM_ID, 1L))
                .willReturn(List.of(task));
        given(todoRepository.findByIdWithLock(TODO_ID)).willReturn(Optional.empty());

        lifecycleService.handleTeamDeparture(TEAM_ID, departing);
    }

    @Test
    void 회원탈퇴_완료기록_익명화는_저장소에_위임한다() {
        lifecycleService.anonymizeFinishedForWithdrawal(1L);

        then(todoWorkItemRepository).should().anonymizeFinishedByAssigneeId(1L);
    }

    private Team team() {
        Team team = Team.create("팀", null, "invite-code");
        setId(team, TEAM_ID);
        return team;
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임" + id, null);
        setId(user, id);
        return user;
    }

    private Todo todo(Team team, TodoMode mode) {
        Todo todo = Todo.create(
                team,
                user(99L),
                "투두",
                null,
                LocalDateTime.now().plusDays(1),
                mode
        );
        setId(todo, TODO_ID);
        return todo;
    }

    private TodoWorkItem direct(Todo todo, User assignee, Long id) {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, assignee);
        setId(workItem, id);
        return workItem;
    }

    private TodoWorkItem task(Todo todo, User assignee, Long id) {
        TodoWorkItem workItem = TodoWorkItem.createTask(
                todo,
                assignee,
                "Task " + id,
                null,
                LocalDateTime.now().plusHours(1),
                id.intValue()
        );
        setId(workItem, id);
        return workItem;
    }

    private void setId(Object target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
    }
}
