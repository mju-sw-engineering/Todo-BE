package com.todo.domain.todo.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoParticipantSummary;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;

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
    private TodoReactionRepository todoReactionRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void 전체_조회는_필터가_없으면_전체_레포지토리_메서드를_호출한다() {
        User user = userWithId(1L);
        Todo todo = todoWithId(10L, TodoStatus.IN_PROGRESS, LocalDateTime.of(2026, 5, 20, 10, 0));
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdWithCreator(100L)).willReturn(List.of(todo));
        given(todoParticipantRepository.findSummaryByTodoIdIn(List.of(10L))).willReturn(List.of(
                participant(10L, 1L, "닉네임1", ParticipantStatus.SUCCESS),
                participant(10L, 2L, "닉네임2", ParticipantStatus.IN_PROGRESS)
        ));

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).achievementCount()).isEqualTo("1 / 2");
        assertThat(response.get(0).myStatus()).isEqualTo("완료");
        assertThat(response.get(0).progressRate()).isEqualTo(50);
        assertThat(response.get(0).participants()).hasSize(2);
        assertThat(response.get(0).participants().get(0).memberId()).isEqualTo(1L);
        assertThat(response.get(0).participants().get(0).nickname()).isEqualTo("닉네임1");
        assertThat(response.get(0).participants().get(0).status()).isEqualTo("완료");
        assertThat(response.get(0).participants().get(1).memberId()).isEqualTo(2L);
        assertThat(response.get(0).participants().get(1).nickname()).isEqualTo("닉네임2");
        assertThat(response.get(0).participants().get(1).status()).isEqualTo("미완료");
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
    void 기간_리포트는_요약_일별통계_액션후보를_계산한다() {
        User user = userWithId(1L);
        Todo successTodo = todoWithId(10L, TodoStatus.SUCCESS, LocalDateTime.of(2026, 5, 20, 10, 0));
        Todo failTodo = todoWithId(11L, TodoStatus.FAIL, LocalDateTime.of(2026, 5, 20, 18, 0));
        Todo progressTodo = todoWithId(12L, TodoStatus.IN_PROGRESS, LocalDateTime.of(2026, 5, 21, 21, 0));
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(
                100L,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 22, 0, 0)
        )).willReturn(List.of(successTodo, failTodo, progressTodo));
        given(todoParticipantRepository.findSummaryByTodoIdIn(List.of(10L, 11L, 12L))).willReturn(List.of(
                participant(10L, 1L, "닉네임1", ParticipantStatus.SUCCESS),
                participant(10L, 2L, "닉네임2", ParticipantStatus.SUCCESS),
                participant(11L, 1L, "닉네임1", ParticipantStatus.FAIL),
                participant(11L, 2L, "닉네임2", ParticipantStatus.IN_PROGRESS),
                participant(12L, 1L, "닉네임1", ParticipantStatus.SUCCESS),
                participant(12L, 2L, "닉네임2", ParticipantStatus.IN_PROGRESS)
        ));

        TodoPeriodReportResponse response = todoService.getTodoPeriodReport(
                100L,
                "user1",
                "2026-05-20",
                "2026-05-21"
        );

        assertThat(response.period().dateCount()).isEqualTo(2);
        assertThat(response.summary().totalTodoCount()).isEqualTo(3);
        assertThat(response.summary().successCount()).isEqualTo(1);
        assertThat(response.summary().failCount()).isEqualTo(1);
        assertThat(response.summary().inProgressCount()).isEqualTo(1);
        assertThat(response.summary().achievementRate()).isEqualTo(33);
        assertThat(response.dailyStats()).hasSize(2);
        assertThat(response.dailyStats().get(0).date()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(response.dailyStats().get(0).achievementRate()).isEqualTo(50);
        assertThat(response.weakestDay().date()).isEqualTo(LocalDate.of(2026, 5, 21));
        assertThat(response.actionCandidates()).hasSize(1);
        assertThat(response.actionCandidates().get(0).todoId()).isEqualTo(12L);
        assertThat(response.actionCandidates().get(0).achievementCount()).isEqualTo(1);
        assertThat(response.actionCandidates().get(0).participantCount()).isEqualTo(2);
        assertThat(response.actionCandidates().get(0).unverifiedCount()).isEqualTo(1);
        assertThat(response.actionCandidates().get(0).progressRate()).isEqualTo(50);
    }

    @Test
    void 기간_리포트는_startDate가_endDate보다_늦으면_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoPeriodReport(
                100L,
                "user1",
                "2026-05-22",
                "2026-05-20"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate는 endDate보다 늦을 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(todoRepository).shouldHaveNoInteractions();
    }

    @Test
    void 기간_리포트는_startDate가_없으면_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoPeriodReport(
                100L,
                "user1",
                null,
                "2026-05-20"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate 파라미터는 필수입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(todoRepository).shouldHaveNoInteractions();
    }

    @Test
    void 기간_리포트는_endDate가_없으면_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoPeriodReport(
                100L,
                "user1",
                "2026-05-20",
                " "
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("endDate 파라미터는 필수입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(todoRepository).shouldHaveNoInteractions();
    }

    @Test
    void 기간_리포트는_날짜_형식이_잘못되면_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoPeriodReport(
                100L,
                "user1",
                "2026/05/20",
                "2026-05-21"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate 형식은 yyyy-MM-dd 이어야 합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(todoRepository).shouldHaveNoInteractions();
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

    @Test
    void 인증_사진을_제출하면_즉시_완료_처리한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participant = todoParticipantWithId(20L, todo, user);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithTodo(10L, 1L)).willReturn(Optional.of(participant));
        given(fileService.createProofThumbnail("proof-key")).willReturn("proof-thumb-key");
        given(todoParticipantRepository.countByTodoId(10L)).willReturn(1L);
        given(todoParticipantRepository.countByTodoIdAndStatus(10L, ParticipantStatus.SUCCESS)).willReturn(1L);

        todoService.submitTodo(10L, "user1", new SubmitTodoRequest("proof-key"));

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.SUCCESS);
        assertThat(participant.getProofImageKey()).isEqualTo("proof-key");
        assertThat(participant.getProofThumbnailKey()).isEqualTo("proof-thumb-key");
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
    }

    @Test
    void 인증_사진_썸네일_생성에_실패해도_원본으로_제출을_완료한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participant = todoParticipantWithId(20L, todo, user);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithTodo(10L, 1L)).willReturn(Optional.of(participant));
        given(fileService.createProofThumbnail("proof-key")).willReturn(null);
        given(todoParticipantRepository.countByTodoId(10L)).willReturn(1L);
        given(todoParticipantRepository.countByTodoIdAndStatus(10L, ParticipantStatus.SUCCESS)).willReturn(1L);

        todoService.submitTodo(10L, "user1", new SubmitTodoRequest("proof-key"));

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.SUCCESS);
        assertThat(participant.getProofImageKey()).isEqualTo("proof-key");
        assertThat(participant.getProofThumbnailKey()).isNull();
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
    }

    @Test
    void 인증_사진_제출_트랜잭션이_실패하면_생성된_썸네일을_삭제한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participantForCheck = todoParticipantWithId(20L, todo, user);
        TodoParticipant alreadySubmittedParticipant = submittedParticipantWithId(20L, todo, user);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithTodo(10L, 1L))
                .willReturn(Optional.of(participantForCheck), Optional.of(alreadySubmittedParticipant));
        given(fileService.createProofThumbnail("proof-key")).willReturn("proof-thumb-key");

        assertThatThrownBy(() -> todoService.submitTodo(10L, "user1", new SubmitTodoRequest("proof-key")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 제출되었거나 완료된 투두입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        then(fileService).should().deleteObject("proof-thumb-key");
    }

    @Test
    void 썸네일_삭제가_실패해도_제출_실패_예외를_유지한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participantForCheck = todoParticipantWithId(20L, todo, user);
        TodoParticipant alreadySubmittedParticipant = submittedParticipantWithId(20L, todo, user);
        IllegalStateException deleteFailure = new IllegalStateException("delete failed");
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithTodo(10L, 1L))
                .willReturn(Optional.of(participantForCheck), Optional.of(alreadySubmittedParticipant));
        given(fileService.createProofThumbnail("proof-key")).willReturn("proof-thumb-key");
        doThrow(deleteFailure).when(fileService).deleteObject("proof-thumb-key");

        assertThatThrownBy(() -> todoService.submitTodo(10L, "user1", new SubmitTodoRequest("proof-key")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 제출되었거나 완료된 투두입니다.")
                .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(deleteFailure));
    }

    @Test
    void 이모지_반응은_없으면_생성한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participant = submittedParticipantWithId(20L, todo, userWithId(2L));
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(participant));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(todoReactionRepository.findByTodoParticipantIdAndUserId(20L, 1L)).willReturn(Optional.empty());
        given(todoReactionRepository.countByTodoParticipantIdAndReactionType(20L, TodoReactionType.LIKE)).willReturn(1L);

        TodoReactionResponse response = todoService.reactTodoParticipant(
                20L,
                "user1",
                new ReactTodoRequest(TodoReactionType.LIKE)
        );

        assertThat(response.type()).isEqualTo(TodoReactionType.LIKE);
        assertThat(response.count()).isEqualTo(1);
        then(todoReactionRepository).should().save(any(TodoReaction.class));
    }

    @Test
    void 이모지_반응은_다른_이모지를_누르면_변경한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participant = submittedParticipantWithId(20L, todo, userWithId(2L));
        TodoReaction reaction = TodoReaction.create(participant, user, TodoReactionType.HEART);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(participant));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(todoReactionRepository.findByTodoParticipantIdAndUserId(20L, 1L)).willReturn(Optional.of(reaction));
        given(todoReactionRepository.countByTodoParticipantIdAndReactionType(20L, TodoReactionType.ANGRY)).willReturn(1L);

        todoService.reactTodoParticipant(20L, "user1", new ReactTodoRequest(TodoReactionType.ANGRY));

        assertThat(reaction.getReactionType()).isEqualTo(TodoReactionType.ANGRY);
    }

    @Test
    void 이모지_반응은_같은_이모지를_다시_누르면_취소한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participant = submittedParticipantWithId(20L, todo, userWithId(2L));
        TodoReaction reaction = TodoReaction.create(participant, user, TodoReactionType.LIKE);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByIdWithTodoAndTeam(20L)).willReturn(Optional.of(participant));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(todoReactionRepository.findByTodoParticipantIdAndUserId(20L, 1L)).willReturn(Optional.of(reaction));
        given(todoReactionRepository.countByTodoParticipantIdAndReactionType(20L, TodoReactionType.LIKE)).willReturn(0L);

        TodoReactionResponse response = todoService.reactTodoParticipant(
                20L,
                "user1",
                new ReactTodoRequest(TodoReactionType.LIKE)
        );

        assertThat(response.count()).isZero();
        then(todoReactionRepository).should().delete(reaction);
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

    private Team teamWithId(Long teamId) {
        Team team = Team.create("팀", null, "invite");
        ReflectionTestUtils.setField(team, "id", teamId);
        return team;
    }

    private Todo todoWithId(Long todoId, TodoStatus status, LocalDateTime deadline) {
        User creator = userWithId(99L);
        Todo todo = Todo.create(null, creator, "투두", "설명", deadline);
        ReflectionTestUtils.setField(todo, "id", todoId);
        ReflectionTestUtils.setField(todo, "status", status);
        return todo;
    }

    private Todo todoWithTeamAndId(Team team, Long todoId, TodoStatus status, LocalDateTime deadline) {
        User creator = userWithId(99L);
        Todo todo = Todo.create(team, creator, "투두", "설명", deadline);
        ReflectionTestUtils.setField(todo, "id", todoId);
        ReflectionTestUtils.setField(todo, "status", status);
        return todo;
    }

    private TodoParticipant todoParticipantWithId(Long participantId, Todo todo, User user) {
        TodoParticipant participant = TodoParticipant.create(todo, user);
        ReflectionTestUtils.setField(participant, "id", participantId);
        return participant;
    }

    private TodoParticipant submittedParticipantWithId(Long participantId, Todo todo, User user) {
        TodoParticipant participant = todoParticipantWithId(participantId, todo, user);
        ReflectionTestUtils.setField(participant, "proofImageKey", "proof-key");
        ReflectionTestUtils.setField(participant, "proofThumbnailKey", "proof-thumb-key");
        ReflectionTestUtils.setField(participant, "status", ParticipantStatus.SUCCESS);
        return participant;
    }

    private TodoParticipantSummary participant(Long todoId, Long userId, String nickname, ParticipantStatus status) {
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
            public String getNickname() {
                return nickname;
            }

            @Override
            public ParticipantStatus getStatus() {
                return status;
            }
        };
    }
}
