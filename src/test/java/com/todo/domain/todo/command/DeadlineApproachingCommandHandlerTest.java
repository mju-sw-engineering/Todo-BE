package com.todo.domain.todo.command;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.command.dto.DeadlineApproachingResult;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoWorkItem;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeadlineApproachingCommandHandlerTest {

    @InjectMocks
    private DeadlineApproachingCommandHandler handler;

    @Mock
    private TodoWorkItemRepository todoWorkItemRepository;

    @Test
    void 명령어는_DEADLINE_APPROACHING이다() {
        assertThat(handler.command()).isEqualTo(SlashCommand.DEADLINE_APPROACHING);
    }

    @Test
    void 진행중인_투두의_미완료_담당자_닉네임을_모아_반환한다() {
        Team team = teamWithId(100L);
        LocalDateTime now = LocalDateTime.now();
        Todo todo = todoWithId(1L, team, now.plusMinutes(20));
        User assignee1 = userWithId(1L, "닉1");
        User assignee2 = userWithId(2L, "닉2");
        TodoWorkItem done = TodoWorkItem.createTask(todo, assignee1, "완료", null, now.plusMinutes(10), 0);
        done.submit("proofs/a.png");
        TodoWorkItem pending1 = TodoWorkItem.createTask(todo, assignee2, "미완료1", null, now.plusMinutes(5), 1);
        TodoWorkItem pending2 = TodoWorkItem.createTask(todo, assignee2, "미완료2", null, now.plusMinutes(15), 2);
        given(todoWorkItemRepository.findByTeamIdAndEffectiveDeadlineBetween(eq(100L), any(), any()))
                .willReturn(List.of(done, pending1, pending2));

        Object result = handler.execute(team, userWithId(9L, "실행자"));

        assertThat(result).isInstanceOf(DeadlineApproachingResult.class);
        DeadlineApproachingResult typed = (DeadlineApproachingResult) result;
        assertThat(typed.items()).hasSize(1);
        DeadlineApproachingResult.Item item = typed.items().get(0);
        assertThat(item.todoId()).isEqualTo(1L);
        assertThat(item.incompleteAssigneeNicknames()).containsExactly("닉2");
    }

    @Test
    void 이미_끝난_투두는_제외한다() {
        Team team = teamWithId(100L);
        LocalDateTime now = LocalDateTime.now();
        Todo finishedTodo = todoWithId(1L, team, now.plusMinutes(10));
        finishedTodo.markAsSuccess();
        User assignee = userWithId(1L, "닉1");
        TodoWorkItem workItem = TodoWorkItem.createTask(finishedTodo, assignee, "이미끝남", null, now.plusMinutes(10), 0);
        given(todoWorkItemRepository.findByTeamIdAndEffectiveDeadlineBetween(eq(100L), any(), any()))
                .willReturn(List.of(workItem));

        DeadlineApproachingResult result = (DeadlineApproachingResult) handler.execute(team, userWithId(9L, "실행자"));

        assertThat(result.items()).isEmpty();
    }

    @Test
    void 모든_WorkItem이_완료된_투두는_제외한다() {
        Team team = teamWithId(100L);
        LocalDateTime now = LocalDateTime.now();
        Todo todo = todoWithId(1L, team, now.plusMinutes(10));
        User assignee = userWithId(1L, "닉1");
        TodoWorkItem done = TodoWorkItem.createTask(todo, assignee, "완료", null, now.plusMinutes(10), 0);
        done.submit("proofs/a.png");
        given(todoWorkItemRepository.findByTeamIdAndEffectiveDeadlineBetween(eq(100L), any(), any()))
                .willReturn(List.of(done));

        DeadlineApproachingResult result = (DeadlineApproachingResult) handler.execute(team, userWithId(9L, "실행자"));

        assertThat(result.items()).isEmpty();
    }

    @Test
    void 미배정_미완료_WorkItem은_담당자_목록에서_빠지지만_투두는_포함된다() {
        Team team = teamWithId(100L);
        LocalDateTime now = LocalDateTime.now();
        Todo todo = todoWithId(1L, team, now.plusMinutes(10));
        TodoWorkItem unassigned = TodoWorkItem.createTask(todo, null, "미배정", null, now.plusMinutes(10), 0);
        given(todoWorkItemRepository.findByTeamIdAndEffectiveDeadlineBetween(eq(100L), any(), any()))
                .willReturn(List.of(unassigned));

        DeadlineApproachingResult result = (DeadlineApproachingResult) handler.execute(team, userWithId(9L, "실행자"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).incompleteAssigneeNicknames()).isEmpty();
    }

    private Team teamWithId(Long id) {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private User userWithId(Long id, String nickname) {
        User user = User.create("user" + id, "encoded", nickname, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Todo todoWithId(Long id, Team team, LocalDateTime deadline) {
        Todo todo = Todo.create(team, userWithId(0L, "생성자"), "제목", null, deadline, TodoMode.TASK);
        ReflectionTestUtils.setField(todo, "id", id);
        return todo;
    }
}
