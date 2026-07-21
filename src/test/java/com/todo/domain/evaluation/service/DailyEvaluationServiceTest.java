package com.todo.domain.evaluation.service;

import com.todo.domain.evaluation.client.AiDailyEvaluationClient;
import com.todo.domain.evaluation.dto.request.AiDailyEvaluationRequest;
import com.todo.domain.evaluation.dto.response.AiDailyEvaluationResponse;
import com.todo.domain.evaluation.dto.response.DailyEvaluationResponse;
import com.todo.domain.evaluation.entity.DailyEvaluation;
import com.todo.domain.evaluation.repository.DailyEvaluationRepository;
import com.todo.domain.team.entity.AiPersona;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TodoDailyEvaluationStat;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DailyEvaluationServiceTest {

    @InjectMocks
    private DailyEvaluationService dailyEvaluationService;

    @Mock
    private DailyEvaluationRepository dailyEvaluationRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TodoRepository todoRepository;
    @Mock
    private AiDailyEvaluationClient aiDailyEvaluationClient;
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
    void 저장된_평가가_있으면_AI_서버를_호출하지_않고_반환한다() {
        User user = userWithId(1L);
        Team team = teamWithId(10L, AiPersona.DEVIL);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        DailyEvaluation evaluation = DailyEvaluation.create(team, yesterday, AiPersona.DEVIL, "캐시된 평가");

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(dailyEvaluationRepository.findByTeamIdAndEvaluationDate(10L, yesterday))
                .willReturn(Optional.of(evaluation));

        DailyEvaluationResponse response = dailyEvaluationService.getDailyEvaluation(10L, "user1");

        assertThat(response.message()).isEqualTo("캐시된 평가");
        assertThat(response.persona()).isEqualTo(AiPersona.DEVIL);
        then(todoRepository).should(never()).findDailyEvaluationStats(anyLong(), any(), any());
        then(aiDailyEvaluationClient).should(never()).createDailyEvaluation(any());
    }

    @Test
    void 저장된_평가가_없으면_통계를_계산하고_AI_서버_응답을_저장한다() {
        User user = userWithId(1L);
        Team team = teamWithId(10L, AiPersona.DEVIL);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        List<TodoDailyEvaluationStat> stats = List.of(
                stat("성공 투두", TodoStatus.SUCCESS, 2, 3),
                stat("진행 투두", TodoStatus.IN_PROGRESS, 1, 3)
        );

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(dailyEvaluationRepository.findByTeamIdAndEvaluationDate(10L, yesterday)).willReturn(Optional.empty());
        given(todoRepository.findDailyEvaluationStats(eq(10L), any(), any())).willReturn(stats);
        given(aiDailyEvaluationClient.createDailyEvaluation(any()))
                .willReturn(new AiDailyEvaluationResponse(AiPersona.DEVIL, "생성된 평가"));
        given(dailyEvaluationRepository.saveAndFlush(any(DailyEvaluation.class))).willAnswer(inv -> inv.getArgument(0));

        DailyEvaluationResponse response = dailyEvaluationService.getDailyEvaluation(10L, "user1");

        assertThat(response.date()).isEqualTo(yesterday);
        assertThat(response.persona()).isEqualTo(AiPersona.DEVIL);
        assertThat(response.message()).isEqualTo("생성된 평가");

        ArgumentCaptor<AiDailyEvaluationRequest> requestCaptor = ArgumentCaptor.forClass(AiDailyEvaluationRequest.class);
        then(aiDailyEvaluationClient).should().createDailyEvaluation(requestCaptor.capture());
        AiDailyEvaluationRequest request = requestCaptor.getValue();
        assertThat(request.summary().totalTodoCount()).isEqualTo(2);
        assertThat(request.summary().successCount()).isEqualTo(1);
        assertThat(request.summary().inProgressCount()).isEqualTo(1);
        assertThat(request.summary().achievementRate()).isEqualTo(50);
        assertThat(request.date()).isEqualTo(yesterday);
        assertThat(request.basisDate()).isEqualTo(yesterday);
        assertThat(request.fallback()).isFalse();
        assertThat(request.todos()).hasSize(2);
    }

    @Test
    void 저장된_평가의_persona가_현재_팀_persona와_다르면_다시_생성해_갱신한다() {
        User user = userWithId(1L);
        Team team = teamWithId(10L, AiPersona.ANGEL);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        DailyEvaluation evaluation = DailyEvaluation.create(team, yesterday, AiPersona.DEVIL, "이전 평가");
        ReflectionTestUtils.setField(evaluation, "id", 100L);
        List<TodoDailyEvaluationStat> stats = List.of(
                stat("성공 투두", TodoStatus.SUCCESS, 2, 3),
                stat("실패 투두", TodoStatus.FAIL, 0, 3)
        );

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(dailyEvaluationRepository.findByTeamIdAndEvaluationDate(10L, yesterday))
                .willReturn(Optional.of(evaluation));
        given(dailyEvaluationRepository.findById(100L)).willReturn(Optional.of(evaluation));
        given(todoRepository.findDailyEvaluationStats(eq(10L), any(), any())).willReturn(stats);
        given(aiDailyEvaluationClient.createDailyEvaluation(any()))
                .willReturn(new AiDailyEvaluationResponse(AiPersona.ANGEL, "새 평가"));

        DailyEvaluationResponse response = dailyEvaluationService.getDailyEvaluation(10L, "user1");

        assertThat(response.persona()).isEqualTo(AiPersona.ANGEL);
        assertThat(response.message()).isEqualTo("새 평가");
        assertThat(evaluation.getPersona()).isEqualTo(AiPersona.ANGEL);
        assertThat(evaluation.getMessage()).isEqualTo("새 평가");
        then(aiDailyEvaluationClient).should().createDailyEvaluation(any());
    }

    @Test
    void 팀_멤버가_아니면_평가를_조회할_수_없다() {
        User user = userWithId(1L);
        Team team = teamWithId(10L, AiPersona.ANGEL);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(false);

        assertThatThrownBy(() -> dailyEvaluationService.getDailyEvaluation(10L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("팀에 접근할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        then(aiDailyEvaluationClient).should(never()).createDailyEvaluation(any());
    }

    @Test
    void 전날_투두가_없으면_최근_이전_투두로_평가를_생성한다() {
        User user = userWithId(1L);
        Team team = teamWithId(10L, AiPersona.ANGEL);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        LocalDate fallbackDate = yesterday.minusDays(3);
        List<TodoDailyEvaluationStat> fallbackStats = List.of(
                stat("이전 성공 투두", TodoStatus.SUCCESS, 2, 2)
        );

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(dailyEvaluationRepository.findByTeamIdAndEvaluationDate(10L, yesterday)).willReturn(Optional.empty());
        given(todoRepository.findDailyEvaluationStats(eq(10L), any(), any()))
                .willReturn(List.of())
                .willReturn(fallbackStats);
        given(todoRepository.findLatestDailyEvaluationTodoDate(eq(10L), any(), any())).willReturn(Optional.of(fallbackDate));
        given(aiDailyEvaluationClient.createDailyEvaluation(any()))
                .willReturn(new AiDailyEvaluationResponse(AiPersona.ANGEL, "이전 투두 기반 평가"));
        given(dailyEvaluationRepository.saveAndFlush(any(DailyEvaluation.class))).willAnswer(inv -> inv.getArgument(0));

        DailyEvaluationResponse response = dailyEvaluationService.getDailyEvaluation(10L, "user1");

        assertThat(response.date()).isEqualTo(yesterday);
        assertThat(response.message()).isEqualTo("이전 투두 기반 평가");

        ArgumentCaptor<AiDailyEvaluationRequest> requestCaptor = ArgumentCaptor.forClass(AiDailyEvaluationRequest.class);
        then(aiDailyEvaluationClient).should().createDailyEvaluation(requestCaptor.capture());
        AiDailyEvaluationRequest request = requestCaptor.getValue();
        assertThat(request.date()).isEqualTo(yesterday);
        assertThat(request.basisDate()).isEqualTo(fallbackDate);
        assertThat(request.fallback()).isTrue();
        assertThat(request.summary().totalTodoCount()).isEqualTo(1);
    }

    @Test
    void 전날과_최근_이전_투두가_모두_없으면_평가를_생성하지_않는다() {
        User user = userWithId(1L);
        Team team = teamWithId(10L, AiPersona.ANGEL);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(dailyEvaluationRepository.findByTeamIdAndEvaluationDate(10L, yesterday)).willReturn(Optional.empty());
        given(todoRepository.findDailyEvaluationStats(eq(10L), any(), any())).willReturn(List.of());
        given(todoRepository.findLatestDailyEvaluationTodoDate(eq(10L), any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> dailyEvaluationService.getDailyEvaluation(10L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("최근 투두가 없어 평가를 생성할 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        then(aiDailyEvaluationClient).should(never()).createDailyEvaluation(any());
    }

    private User userWithId(Long userId) {
        User user = User.create("user" + userId, "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Team teamWithId(Long teamId, AiPersona persona) {
        Team team = Team.create("팀", null, "ABCDEFGH", persona);
        ReflectionTestUtils.setField(team, "id", teamId);
        return team;
    }

    private TodoDailyEvaluationStat stat(String title, TodoStatus status, long achievementCount, long participantCount) {
        return new TodoDailyEvaluationStat() {
            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public TodoStatus getStatus() {
                return status;
            }

            @Override
            public LocalDateTime getDeadline() {
                return LocalDateTime.of(2026, 5, 20, 20, 0);
            }

            @Override
            public long getAchievementCount() {
                return achievementCount;
            }

            @Override
            public long getParticipantCount() {
                return participantCount;
            }
        };
    }
}
