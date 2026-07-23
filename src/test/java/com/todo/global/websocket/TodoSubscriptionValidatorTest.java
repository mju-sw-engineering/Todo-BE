package com.todo.global.websocket;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoSubscriptionValidatorTest {

    @InjectMocks
    private TodoSubscriptionValidator validator;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void 채팅_채널_구독은_팀원이면_통과한다() {
        Todo todo = todoWithTeamId(10L, 100L);
        User user = userWithId(1L);
        given(todoRepository.findByIdWithCreatorAndTeam(10L)).willReturn(Optional.of(todo));
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);

        assertThatCode(() -> validator.validate("/topic/todos/10", "user1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 타이핑_채널_구독은_팀원이면_통과한다() {
        Todo todo = todoWithTeamId(10L, 100L);
        User user = userWithId(1L);
        given(todoRepository.findByIdWithCreatorAndTeam(10L)).willReturn(Optional.of(todo));
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);

        assertThatCode(() -> validator.validate("/topic/todos/10/typing", "user1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 채팅_채널_구독은_팀원이_아니면_예외를_던진다() {
        Todo todo = todoWithTeamId(10L, 100L);
        User user = userWithId(1L);
        given(todoRepository.findByIdWithCreatorAndTeam(10L)).willReturn(Optional.of(todo));
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> validator.validate("/topic/todos/10", "user1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("해당 채널을 구독할 권한이 없습니다.");
    }

    @Test
    void 존재하지_않는_투두_채널_구독은_예외를_던진다() {
        given(todoRepository.findByIdWithCreatorAndTeam(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("/topic/todos/99", "user1"))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("존재하지 않는 투두입니다.");
    }

    @Test
    void 보호_대상이_아닌_destination은_검증을_건너뛴다() {
        assertThatCode(() -> validator.validate("/queue/notifications", "user1"))
                .doesNotThrowAnyException();

        then(todoRepository).shouldHaveNoInteractions();
        then(teamMemberRepository).shouldHaveNoInteractions();
    }

    @Test
    void destination이_null이면_검증을_건너뛴다() {
        assertThatCode(() -> validator.validate(null, "user1"))
                .doesNotThrowAnyException();

        then(todoRepository).shouldHaveNoInteractions();
    }

    private Todo todoWithTeamId(Long todoId, Long teamId) {
        Team team = Team.create("팀", "설명", null);
        ReflectionTestUtils.setField(team, "id", teamId);
        User creator = User.create("creator", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(creator, "id", 99L);
        Todo todo = Todo.create(team, creator, "투두", "설명", null);
        ReflectionTestUtils.setField(todo, "id", todoId);
        return todo;
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임" + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
