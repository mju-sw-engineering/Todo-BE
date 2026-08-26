package com.todo.domain.todo.command;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.command.dto.TeamStatusResult;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TeamStatusTodoProgress;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoStatusCount;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
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
    void 상태별_투두_개수와_진행중_투두별_WorkItem_완료_현황을_반환한다() {
        Team team = teamWithId(100L);
        given(todoRepository.countByTeamIdGroupByStatus(100L)).willReturn(List.of(
                countOf(TodoStatus.IN_PROGRESS, 2L),
                countOf(TodoStatus.SUCCESS, 5L)
        ));
        given(todoWorkItemRepository.findInProgressTodoProgressByTeamId(100L)).willReturn(List.of(
                progressOf(1L, "기말 발표", 1L, 3L),
                progressOf(2L, "회의록 작성", 0L, 2L)
        ));

        Object result = handler.execute(team, userWithId(1L));

        assertThat(result).isInstanceOf(TeamStatusResult.class);
        TeamStatusResult typed = (TeamStatusResult) result;
        assertThat(typed.inProgressCount()).isEqualTo(2L);
        assertThat(typed.successCount()).isEqualTo(5L);
        assertThat(typed.failCount()).isEqualTo(0L);
        assertThat(typed.inProgressTodos()).containsExactly(
                new TeamStatusResult.InProgressTodo(1L, "기말 발표", 1L, 3L),
                new TeamStatusResult.InProgressTodo(2L, "회의록 작성", 0L, 2L)
        );
    }

    @Test
    void 데이터가_없으면_0과_빈_목록을_반환한다() {
        Team team = teamWithId(100L);
        given(todoRepository.countByTeamIdGroupByStatus(100L)).willReturn(List.of());
        given(todoWorkItemRepository.findInProgressTodoProgressByTeamId(100L)).willReturn(List.of());

        TeamStatusResult result = (TeamStatusResult) handler.execute(team, userWithId(1L));

        assertThat(result.inProgressCount()).isZero();
        assertThat(result.successCount()).isZero();
        assertThat(result.failCount()).isZero();
        assertThat(result.inProgressTodos()).isEmpty();
    }

    private TodoStatusCount countOf(TodoStatus status, long count) {
        return new TodoStatusCount() {
            public TodoStatus getStatus() { return status; }
            public long getCount() { return count; }
        };
    }

    private TeamStatusTodoProgress progressOf(Long todoId, String title, long completedCount, long totalCount) {
        return new TeamStatusTodoProgress() {
            public Long getTodoId() { return todoId; }
            public String getTitle() { return title; }
            public long getCompletedCount() { return completedCount; }
            public long getTotalCount() { return totalCount; }
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
