package com.todo.domain.todo.service;

import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
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
import com.todo.domain.todo.repository.TodoParticipantDetail;
import com.todo.domain.todo.repository.TodoParticipantSummary;
import com.todo.domain.todo.repository.TodoReactionCount;
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
import java.time.OffsetDateTime;
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
    @Mock
    private NotificationService notificationService;

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

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", null, null);

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

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", "IN_PROGRESS", null);

        assertThat(response).isEmpty();
        then(todoRepository).should().findByTeamIdAndStatusWithCreator(100L, TodoStatus.IN_PROGRESS);
    }

    @Test
    void 종료_필터는_SUCCESS_FAIL_상태를_조회한다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndStatusInWithCreator(100L, List.of(TodoStatus.SUCCESS, TodoStatus.FAIL)))
                .willReturn(List.of());

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", "ENDED", null);

        assertThat(response).isEmpty();
        then(todoRepository).should()
                .findByTeamIdAndStatusInWithCreator(100L, List.of(TodoStatus.SUCCESS, TodoStatus.FAIL));
    }

    @Test
    void 알수없는_필터는_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoList(100L, "user1", "UNKNOWN", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("알 수 없는 투두 필터입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 날짜_파라미터_조회는_해당_날짜_하루_범위를_전달한다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(eq(100L), any(), any()))
                .willReturn(List.of());

        todoService.getTodoList(100L, "user1", null, "2026-05-20");

        then(todoRepository).should().findByTeamIdAndDeadlineBetweenWithCreator(
                100L,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 21, 0, 0)
        );
    }

    @Test
    void 필터와_날짜를_함께_사용하면_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoList(100L, "user1", "ENDED", "2026-05-20"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("filter와 date는 함께 사용할 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 날짜_형식이_잘못되면_400_예외를_던진다() {
        User user = userWithId(1L);
        givenValidTeamMember(user);

        assertThatThrownBy(() -> todoService.getTodoList(100L, "user1", null, "2026/05/20"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("date 형식은 yyyy-MM-dd 이어야 합니다.")
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
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime futureDeadline = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);
        Todo successTodo = todoWithId(10L, TodoStatus.SUCCESS, yesterday.atTime(10, 0));
        Todo failTodo = todoWithId(11L, TodoStatus.FAIL, yesterday.atTime(18, 0));
        Todo progressTodo = todoWithId(12L, TodoStatus.IN_PROGRESS, futureDeadline);
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(
                100L,
                yesterday.atStartOfDay(),
                tomorrow.plusDays(1).atStartOfDay()
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
                yesterday.toString(),
                tomorrow.toString()
        );

        assertThat(response.period().dateCount()).isEqualTo(3);
        assertThat(response.summary().totalTodoCount()).isEqualTo(3);
        assertThat(response.summary().successCount()).isEqualTo(1);
        assertThat(response.summary().failCount()).isEqualTo(1);
        assertThat(response.summary().inProgressCount()).isEqualTo(1);
        assertThat(response.summary().achievementRate()).isEqualTo(33);
        assertThat(response.dailyStats()).hasSize(3);
        assertThat(response.dailyStats().get(0).date()).isEqualTo(yesterday);
        assertThat(response.dailyStats().get(0).achievementRate()).isEqualTo(50);
        assertThat(response.weakestDay().date()).isEqualTo(tomorrow);
        assertThat(response.actionCandidates()).hasSize(1);
        assertThat(response.actionCandidates().get(0).todoId()).isEqualTo(12L);
        assertThat(response.actionCandidates().get(0).achievementCount()).isEqualTo(1);
        assertThat(response.actionCandidates().get(0).participantCount()).isEqualTo(2);
        assertThat(response.actionCandidates().get(0).unverifiedCount()).isEqualTo(1);
        assertThat(response.actionCandidates().get(0).progressRate()).isEqualTo(50);
    }

    @Test
    void 마감이_지난_진행중_투두는_응답에서_실패로_표시된다() {
        User user = userWithId(1L);
        Todo expiredTodo = todoWithId(10L, TodoStatus.IN_PROGRESS, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1));
        givenValidTeamMember(user);
        given(todoRepository.findByTeamIdWithCreator(100L)).willReturn(List.of(expiredTodo));
        given(todoParticipantRepository.findSummaryByTodoIdIn(List.of(10L))).willReturn(List.of(
                participant(10L, 1L, "닉네임1", ParticipantStatus.IN_PROGRESS)
        ));

        List<TodoSummaryResponse> response = todoService.getTodoList(100L, "user1", null, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).status()).isEqualTo(TodoStatus.FAIL);
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

        assertThatThrownBy(() -> todoService.getTodoList(100L, "user1", null, null))
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
        given(todoParticipantRepository.findByTodoIdAndUserIdWithLock(10L, 1L)).willReturn(Optional.of(participant));
        given(fileService.createProofThumbnail("proof-key")).willReturn("proof-thumb-key");
        given(todoParticipantRepository.countByTodoId(10L)).willReturn(1L);
        given(todoParticipantRepository.countByTodoIdAndStatus(10L, ParticipantStatus.SUCCESS)).willReturn(1L);

        todoService.submitTodo(10L, "user1", new SubmitTodoRequest("proof-key"));

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.SUCCESS);
        assertThat(participant.getProofImageKey()).isEqualTo("proof-key");
        assertThat(participant.getProofThumbnailKey()).isEqualTo("proof-thumb-key");
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.SUCCESS);
        then(todoParticipantRepository).should().findByTodoIdAndUserIdWithLock(10L, 1L);
        then(teamRepository).should().incrementSuccessCount(100L);
    }

    @Test
    void 인증_사진_썸네일_생성에_실패해도_원본으로_제출을_완료한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        TodoParticipant participant = todoParticipantWithId(20L, todo, user);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithTodo(10L, 1L)).willReturn(Optional.of(participant));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithLock(10L, 1L)).willReturn(Optional.of(participant));
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
                .willReturn(Optional.of(participantForCheck));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithLock(10L, 1L))
                .willReturn(Optional.of(alreadySubmittedParticipant));
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
                .willReturn(Optional.of(participantForCheck));
        given(todoParticipantRepository.findByTodoIdAndUserIdWithLock(10L, 1L))
                .willReturn(Optional.of(alreadySubmittedParticipant));
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

    @Test
    void 투두_생성_성공() {
        User creator = userWithId(1L);
        User assignee = userWithId(2L);
        Team team = teamWithId(100L);
        OffsetDateTime deadline = OffsetDateTime.parse("2026-06-04T12:00:00+09:00");
        CreateTodoRequest request = new CreateTodoRequest("투두", "설명", deadline, List.of(2L));
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(creator));
        given(teamRepository.findById(100L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(todoRepository.save(any(Todo.class))).willAnswer(invocation -> {
            Todo todo = invocation.getArgument(0);
            ReflectionTestUtils.setField(todo, "id", 10L);
            return todo;
        });
        given(userRepository.findById(2L)).willReturn(Optional.of(assignee));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).willReturn(true);

        CreateTodoResponse response = todoService.createTodo("user1", 100L, request);

        assertThat(response.todoId()).isEqualTo(10L);
        assertThat(response.teamId()).isEqualTo(100L);
        assertThat(response.creatorId()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("투두");
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.assigneeIds()).containsExactly(2L);
        then(todoParticipantRepository).should().save(any(TodoParticipant.class));
    }

    @Test
    void 투두_생성시_본인을_제외한_팀원에게_알림을_일괄_발송한다() {
        User creator = userWithId(1L);
        User other = userWithId(3L);
        Team team = teamWithId(100L);
        OffsetDateTime deadline = OffsetDateTime.parse("2026-06-04T12:00:00+09:00");
        CreateTodoRequest request = new CreateTodoRequest("투두", "설명", deadline, List.of());
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(creator));
        given(teamRepository.findById(100L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(todoRepository.save(any(Todo.class))).willAnswer(invocation -> {
            Todo todo = invocation.getArgument(0);
            ReflectionTestUtils.setField(todo, "id", 10L);
            return todo;
        });
        given(teamMemberRepository.findByTeamIdExcludingUser(100L, 1L))
                .willReturn(List.of(TeamMember.create(team, other, TeamMemberRole.MEMBER)));

        todoService.createTodo("user1", 100L, request);

        then(notificationService).should().sendAll(
                eq(List.of(other)), eq(NotificationType.TODO_CREATED), any(), any(), eq(10L));
    }

    @Test
    void 투두_생성은_팀원이_아니면_403_예외를_던진다() {
        User creator = userWithId(1L);
        Team team = teamWithId(100L);
        CreateTodoRequest request = new CreateTodoRequest(
                "투두",
                null,
                OffsetDateTime.parse("2026-06-04T12:00:00+09:00"),
                List.of(2L)
        );
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(creator));
        given(teamRepository.findById(100L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> todoService.createTodo("user1", 100L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 투두_상세_조회_성공() {
        User viewer = userWithId(1L);
        User creator = userWithId(99L);
        Team team = teamWithId(100L);
        Todo todo = Todo.create(team, creator, "투두", "설명", LocalDateTime.of(2026, 6, 4, 12, 0));
        ReflectionTestUtils.setField(todo, "id", 10L);
        TodoParticipant mine = submittedParticipantWithId(20L, todo, viewer);
        TodoReaction myReaction = TodoReaction.create(mine, viewer, TodoReactionType.LIKE);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(viewer));
        given(todoRepository.findByIdWithCreatorAndTeam(10L)).willReturn(Optional.of(todo));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(todoParticipantRepository.findDetailByTodoId(10L)).willReturn(List.of(
                participantDetail(20L, 1L, "닉네임1", "profiles/1.png", "proof-key", "proof-thumb-key", ParticipantStatus.SUCCESS),
                participantDetail(21L, 2L, "닉네임2", null, null, null, ParticipantStatus.IN_PROGRESS)
        ));
        given(todoReactionRepository.countByTodoParticipantIds(List.of(20L, 21L))).willReturn(List.of(
                reactionCount(20L, TodoReactionType.LIKE, 2L)
        ));
        given(todoReactionRepository.findByTodoParticipantIdInAndUserId(List.of(20L, 21L), 1L))
                .willReturn(List.of(myReaction));
        given(fileService.resolveImageUrl("profiles/1.png")).willReturn("profile-url");
        given(fileService.resolveImageUrl("proof-key")).willReturn("proof-url");
        given(fileService.resolveImageUrl("proof-thumb-key")).willReturn("thumb-url");

        TodoDetailResponse response = todoService.getTodoDetail(10L, "user1");

        assertThat(response.todoId()).isEqualTo(10L);
        assertThat(response.achievementCount()).isEqualTo("1 / 2");
        assertThat(response.participants()).hasSize(2);
        assertThat(response.participants().get(0).profileImageUrl()).isEqualTo("profile-url");
        assertThat(response.participants().get(0).proofImageUrl()).isEqualTo("proof-url");
        assertThat(response.participants().get(0).proofThumbnailUrl()).isEqualTo("thumb-url");
        assertThat(response.participants().get(0).myReaction()).isEqualTo(TodoReactionType.LIKE);
        assertThat(response.participants().get(0).reactions())
                .anySatisfy(reaction -> {
                    assertThat(reaction.type()).isEqualTo(TodoReactionType.LIKE);
                    assertThat(reaction.count()).isEqualTo(2);
                });
    }

    @Test
    void 투두_상세_조회는_참여자가_없어도_빈_반응맵으로_응답한다() {
        User viewer = userWithId(1L);
        Team team = teamWithId(100L);
        Todo todo = todoWithTeamAndId(team, 10L, TodoStatus.IN_PROGRESS, LocalDateTime.of(2026, 6, 4, 12, 0));
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(viewer));
        given(todoRepository.findByIdWithCreatorAndTeam(10L)).willReturn(Optional.of(todo));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(todoParticipantRepository.findDetailByTodoId(10L)).willReturn(List.of());

        TodoDetailResponse response = todoService.getTodoDetail(10L, "user1");

        assertThat(response.achievementCount()).isEqualTo("0 / 0");
        assertThat(response.participants()).isEmpty();
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

    private TodoParticipantDetail participantDetail(
            Long todoParticipantId,
            Long userId,
            String nickname,
            String profileImageUrl,
            String proofImageKey,
            String proofThumbnailKey,
            ParticipantStatus status
    ) {
        return new TodoParticipantDetail() {
            @Override
            public Long getTodoParticipantId() {
                return todoParticipantId;
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
            public String getProfileImageUrl() {
                return profileImageUrl;
            }

            @Override
            public String getProofImageKey() {
                return proofImageKey;
            }

            @Override
            public String getProofThumbnailKey() {
                return proofThumbnailKey;
            }

            @Override
            public ParticipantStatus getStatus() {
                return status;
            }
        };
    }

    private TodoReactionCount reactionCount(Long todoParticipantId, TodoReactionType reactionType, long reactionCount) {
        return new TodoReactionCount() {
            @Override
            public Long getTodoParticipantId() {
                return todoParticipantId;
            }

            @Override
            public TodoReactionType getReactionType() {
                return reactionType;
            }

            @Override
            public long getReactionCount() {
                return reactionCount;
            }
        };
    }
}
