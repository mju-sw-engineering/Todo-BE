package com.todo.domain.todo.command;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.command.dto.TeamStatusResult;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoStatusCount;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.WorkItemStatusCount;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TeamStatusCommandHandlerTest {

    @InjectMocks
    private TeamStatusCommandHandler handler;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;

    @Test
    void 명령어는_TEAM_STATUS이다() {
        assertThat(handler.command()).isEqualTo(SlashCommand.TEAM_STATUS);
    }

    @Test
    void 상태별_투두_개수와_진행중_투두의_WorkItem_완료_현황을_반환한다() {
        Team team = teamWithId(100L);
        given(todoRepository.countByTeamIdGroupByStatus(100L)).willReturn(List.of(
                countOf(TodoStatus.IN_PROGRESS, 3L),
                countOf(TodoStatus.SUCCESS, 5L)
        ));
        given(todoWorkItemRepository.countByTeamIdAndTodoInProgressGroupByStatus(100L)).willReturn(List.of(
                workItemCountOf(WorkItemStatus.SUCCESS, 4L),
                workItemCountOf(WorkItemStatus.IN_PROGRESS, 6L)
        ));

        Object result = handler.execute(team, userWithId(1L));

        assertThat(result).isInstanceOf(TeamStatusResult.class);
        TeamStatusResult typed = (TeamStatusResult) result;
        assertThat(typed.inProgressTodoCount()).isEqualTo(3L);
        assertThat(typed.successTodoCount()).isEqualTo(5L);
        assertThat(typed.failTodoCount()).isEqualTo(0L);
        assertThat(typed.inProgressWorkItemTotal()).isEqualTo(10L);
        assertThat(typed.inProgressWorkItemCompletedCount()).isEqualTo(4L);
    }

    @Test
    void 데이터가_없는_상태는_0으로_채운다() {
        Team team = teamWithId(100L);
        given(todoRepository.countByTeamIdGroupByStatus(100L)).willReturn(List.of());
        given(todoWorkItemRepository.countByTeamIdAndTodoInProgressGroupByStatus(100L)).willReturn(List.of());

        TeamStatusResult result = (TeamStatusResult) handler.execute(team, userWithId(1L));

        assertThat(result.inProgressTodoCount()).isZero();
        assertThat(result.successTodoCount()).isZero();
        assertThat(result.failTodoCount()).isZero();
        assertThat(result.inProgressWorkItemTotal()).isZero();
        assertThat(result.inProgressWorkItemCompletedCount()).isZero();
    }

    private TodoStatusCount countOf(TodoStatus status, long count) {
        return new TodoStatusCount() {
            public TodoStatus getStatus() { return status; }
            public long getCount() { return count; }
        };
    }

    private WorkItemStatusCount workItemCountOf(WorkItemStatus status, long count) {
        return new WorkItemStatusCount() {
            public WorkItemStatus getStatus() { return status; }
            public long getCount() { return count; }
        };
    }

    private Team teamWithId(Long id) {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉" + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
