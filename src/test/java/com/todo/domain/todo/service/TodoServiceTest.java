package com.todo.domain.todo.service;

import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoParticipantSummary;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoVoteRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @InjectMocks
    private TodoService todoService;

    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TodoParticipantRepository todoParticipantRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileService fileService;
    @Mock
    private TodoVoteRepository todoVoteRepository;

    @Test
    void 전체_조회는_필터가_없으면_전체_레포지토리_메서드를_호출한다() {
        User user = userWithId(1L);
        Todo todo = todoWithId(10L, TodoStatus.IN_PROGRESS, LocalDateTime.of(2026, 5, 20, 10, 0));
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdWithCreator(100L)).willReturn(List.of(todo));
        given(todoParticipantRepository.findSummaryByTodoIdIn(List.of(10L))).willReturn(List.of(
                participant(10L, 1L, ParticipantStatus.SUCCESS),
                participant(10L, 2L, ParticipantStatus.IN_PROGRESS)
        ));

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).achievementCount()).isEqualTo("1 / 2");
        assertThat(response.get(0).myStatus()).isEqualTo("완료");
        assertThat(response.get(0).progressRate()).isEqualTo(50);
        then(todoRepository).should().findByTeamIdWithCreator(100L);
    }

    @Test
    void 진행중_필터는_IN_PROGRESS_상태만_조회한다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndStatusWithCreator(100L, TodoStatus.IN_PROGRESS))
                .willReturn(List.of());

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", "IN_PROGRESS");

        assertThat(response).isEmpty();
        then(todoRepository).should().findByTeamIdAndStatusWithCreator(100L, TodoStatus.IN_PROGRESS);
    }

    @Test
    void 종료_필터는_SUCCESS_FAIL_상태를_조회한다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndStatusInWithCreator(100L, List.of(TodoStatus.SUCCESS, TodoStatus.FAIL)))
                .willReturn(List.of());

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", "ENDED");

        assertThat(response).isEmpty();
        then(todoRepository).should()
                .findByTeamIdAndStatusInWithCreator(100L, List.of(TodoStatus.SUCCESS, TodoStatus.FAIL));
    }

    @Test
    void 알수없는_필터는_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoList(100L, "user1", "UNKNOWN"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("알 수 없는 투두 필터입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 오늘_조회는_Asia_Seoul_오늘_범위를_전달한다() {
        User user = userWithId(1L);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(eq(100L), any(), any()))
                .willReturn(List.of());

        todoService.getTodayTodoList(100L, "user1");

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(todoRepository).should()
                .findByTeamIdAndDeadlineBetweenWithCreator(eq(100L), startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(today.atStartOfDay());
        assertThat(endCaptor.getValue()).isEqualTo(today.plusDays(1).atStartOfDay());
    }

    @Test
    void 특정날짜_조회는_date_기준_하루_범위를_전달한다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(eq(100L), any(), any()))
                .willReturn(List.of());

        todoService.getTodoHistory(100L, "user1", "2026-05-20");

        then(todoRepository).should().findByTeamIdAndDeadlineBetweenWithCreator(
                100L,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 21, 0, 0)
        );
    }

    @Test
    void 특정날짜_조회는_date가_없거나_잘못되면_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoHistory(100L, "user1", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> todoService.getTodoHistory(100L, "user1", "2026/05/20"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("date 형식은 yyyy-MM-dd 이어야 합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 팀_멤버가_아니면_403_예외를_던진다() {
        User user = userWithId(1L);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.existsById(100L)).willReturn(true);
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> todoService.getTodoList(100L, "user1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void givenValidTeamMember(User user) {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.existsById(100L)).willReturn(true);
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, user.getId())).willReturn(true);
    }

    private User userWithId(Long userId) {
        User user = User.create("user" + userId, "encodedPwd", "닉네임" + userId, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Todo todoWithId(Long todoId, TodoStatus status, LocalDateTime deadline) {
        User creator = userWithId(99L);
        Todo todo = Todo.create(null, creator, "투두", "설명", deadline);
        ReflectionTestUtils.setField(todo, "id", todoId);
        ReflectionTestUtils.setField(todo, "status", status);
        return todo;
    }

    private TodoParticipantSummary participant(Long todoId, Long userId, ParticipantStatus status) {
        return new TodoParticipantSummary() {
            @Override
            public Long getTodoId() {
                return todoId;
            }

            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public ParticipantStatus getStatus() {
                return status;
            }
        };
    }
}
