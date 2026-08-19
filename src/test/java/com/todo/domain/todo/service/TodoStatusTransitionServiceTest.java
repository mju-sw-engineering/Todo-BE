package com.todo.domain.todo.service;

import com.todo.domain.notification.entity.NotificationType;
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
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoStatusTransitionServiceTest {

    private static final Long TEAM_ID = 100L;
    private static final Long TODO_ID = 10L;

    @InjectMocks
    private TodoStatusTransitionService transitionService;

    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    @Test
    void 실행_항목이_하나도_없으면_Todo를_실패로_확정한다() {
        Todo todo = todo(TodoStatus.IN_PROGRESS);
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(0L);

        transitionService.reevaluate(todo);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.FAIL);
        then(teamRepository).shouldHaveNoInteractions();
    }

    @Test
    void 실패한_항목이_하나라도_있으면_나머지_성공과_무관하게_실패로_확정한다() {
        Todo todo = todo(TodoStatus.IN_PROGRESS);
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(3L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(1L);

        transitionService.reevaluate(todo);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.FAIL);
        then(teamRepository).shouldHaveNoInteractions();
    }

    @Test
    void 남은_항목이_전원_성공이면_Todo를_성공으로_전이하고_팀_카운터를_1_증가시킨다() {
        Todo todo = todo(TodoStatus.IN_PROGRESS);
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(2L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(0L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.SUCCESS)).willReturn(2L);
        User member = user();
        TeamMember teamMember = TeamMember.create(todo.getTeam(), member, TeamMemberRole.MEMBER);
        given(teamMemberRepository.findByTeamIdWithUser(TEAM_ID)).willReturn(List.of(teamMember));
        NotificationMessage message = new NotificationMessage(NotificationType.TODO_ALL_COMPLETED, "title", "content");
        given(notificationMessageFactory.todoAllCompleted(todo.getTitle())).willReturn(message);

        transitionService.reevaluate(todo);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
        then(teamRepository).should().incrementSuccessCount(TEAM_ID);
        then(notificationService).should().sendAll(List.of(member), null, message, TODO_ID, TEAM_ID);
    }

    @Test
    void 아직_미완료_항목이_남아_있으면_진행중을_유지하고_카운터를_올리지_않는다() {
        Todo todo = todo(TodoStatus.IN_PROGRESS);
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(3L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(0L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.SUCCESS)).willReturn(2L);

        transitionService.reevaluate(todo);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
        then(teamRepository).shouldHaveNoInteractions();
    }

    @Test
    void 이미_성공한_Todo를_다시_재평가해도_팀_카운터는_중복_증가하지_않는다() {
        Todo todo = todo(TodoStatus.SUCCESS);
        given(todoWorkItemRepository.countByTodoId(TODO_ID)).willReturn(1L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.FAIL)).willReturn(0L);
        given(todoWorkItemRepository.countByTodoIdAndStatus(TODO_ID, WorkItemStatus.SUCCESS)).willReturn(1L);

        transitionService.reevaluate(todo);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
        then(teamRepository).shouldHaveNoInteractions();
    }

    @Test
    void 마감_초과는_WorkItem과_부모_Todo를_함께_실패로_확정한다() {
        Todo todo = todo(TodoStatus.IN_PROGRESS);
        TodoWorkItem workItem = direct(todo);
        NotificationMessage message = new NotificationMessage(NotificationType.TODO_WORK_ITEM_EXPIRED, "title", "content");
        given(notificationMessageFactory.todoWorkItemExpired(todo.getTitle())).willReturn(message);

        transitionService.failOnDeadlinePassed(todo, workItem);

        assertThat(workItem.getStatus()).isEqualTo(WorkItemStatus.FAIL);
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.FAIL);
        then(notificationService).should().send(workItem.getAssignee(), null, message, TODO_ID, TEAM_ID);
    }

    @Test
    void 마감_초과_처리는_이미_확정된_Todo의_상태를_되돌리지_않는다() {
        Todo todo = todo(TodoStatus.SUCCESS);
        TodoWorkItem workItem = direct(todo);
        NotificationMessage message = new NotificationMessage(NotificationType.TODO_WORK_ITEM_EXPIRED, "title", "content");
        given(notificationMessageFactory.todoWorkItemExpired(todo.getTitle())).willReturn(message);

        transitionService.failOnDeadlinePassed(todo, workItem);

        assertThat(workItem.getStatus()).isEqualTo(WorkItemStatus.FAIL);
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
    }

    private Todo todo(TodoStatus status) {
        Team team = Team.create("팀", null, "invite-code");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        Todo todo = Todo.create(
                team,
                user(),
                "투두",
                null,
                LocalDateTime.now().plusDays(1),
                TodoMode.DIRECT
        );
        ReflectionTestUtils.setField(todo, "id", TODO_ID);
        ReflectionTestUtils.setField(todo, "status", status);
        return todo;
    }

    private TodoWorkItem direct(Todo todo) {
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, user());
        ReflectionTestUtils.setField(workItem, "id", 20L);
        return workItem;
    }

    private User user() {
        User user = User.create("user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
