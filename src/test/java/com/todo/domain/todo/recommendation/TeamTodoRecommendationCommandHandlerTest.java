package com.todo.domain.todo.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationItem;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationResult;
import com.todo.domain.user.entity.User;
import com.todo.global.ai.AiClientException;
import com.todo.global.ai.AiStructuredRequest;
import com.todo.global.ai.OpenAiClient;
import com.todo.global.ai.OpenAiProperties;
import com.todo.global.ratelimit.SimpleRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TeamTodoRecommendationCommandHandlerTest {

    private static final Long TEAM_ID = 100L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);
    private static final Instant NOW = LocalDateTime.of(2026, 8, 24, 10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();

    @Mock private TeamActivityDigestBuilder digestBuilder;
    @Mock private RecommendationPromptProvider promptProvider;
    @Mock private RecommendationResultSanitizer sanitizer;
    @Mock private OpenAiClient openAiClient;
    @Mock private OpenAiProperties openAiProperties;
    @Mock private SimpleRateLimiter rateLimiter;
    @Mock private SlashCommandExecutionRepository executionRepository;

    private TeamTodoRecommendationCommandHandler handler;
    private Team team;
    private User executor;

    @BeforeEach
    void setUp() {
        handler = newHandler(new TodoRecommendationProperties(true, null, null, null));
        team = Team.create("팀", "설명", null, "INVITE01");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        executor = User.create("user", "pw", "민수", null);
        ReflectionTestUtils.setField(executor, "id", 1L);
        lenient().when(openAiProperties.apiKey()).thenReturn("sk-test");
        lenient().when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        lenient().when(executionRepository.findTop20ByTeamIdAndCommandOrderByIdDesc(any(), any()))
                .thenReturn(List.of());
        lenient().when(promptProvider.systemInstruction(any())).thenReturn("system");
        lenient().when(promptProvider.userText(any())).thenReturn("user");
        lenient().when(promptProvider.schema()).thenReturn(Map.of("type", "object"));
    }

    private TeamTodoRecommendationCommandHandler newHandler(TodoRecommendationProperties properties) {
        return new TeamTodoRecommendationCommandHandler(
                digestBuilder, promptProvider, sanitizer, openAiClient, openAiProperties,
                properties, rateLimiter, executionRepository, new ObjectMapper(),
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 명령어는_팀_스코프의_할일추천이다() {
        assertThat(handler.command()).isEqualTo(SlashCommand.TODO_RECOMMENDATION);
    }

    @Test
    void 정상이면_READY_카드를_돌려준다() {
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willReturn(new AiRecommendationResponse("관찰", "이렇게 해봐요", List.of()));
        given(sanitizer.sanitize(any(), any())).willReturn(List.of(item()));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.READY);
        assertThat(result.greeting()).isEqualTo("이렇게 해봐요");
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void STARTER_모드는_STARTER_결과가_된다() {
        givenDigest(RecommendationMode.STARTER);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willReturn(new AiRecommendationResponse("관찰", "시작해볼까요", List.of()));
        given(sanitizer.sanitize(any(), any())).willReturn(List.of(item()));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.STARTER);
    }

    @Test
    void 검증_후_남은_항목이_없으면_EMPTY다() {
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willReturn(new AiRecommendationResponse("관찰", "잘 하고 있어요", List.of()));
        given(sanitizer.sanitize(any(), any())).willReturn(List.of());

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.EMPTY);
    }

    @Test
    void NONE_모드는_모델을_부르지_않는다() {
        givenDigest(RecommendationMode.NONE);

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.NONE);
        assertThat(result.greeting()).contains("아직 팀 기록이 없어서");
        then(openAiClient).should(never()).generateStructured(any(), any(), anyString());
    }

    @Test
    void 기능이_꺼져있으면_UNAVAILABLE이고_요약도_만들지_않는다() {
        handler = newHandler(new TodoRecommendationProperties(false, null, null, null));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.UNAVAILABLE);
        then(digestBuilder).should(never()).build(any(), any());
        then(rateLimiter).should(never()).tryAcquire(anyString(), anyInt(), any());
    }

    @Test
    void OpenAI_키가_없으면_UNAVAILABLE이다() {
        given(openAiProperties.apiKey()).willReturn("  ");

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.UNAVAILABLE);
        then(openAiClient).should(never()).generateStructured(any(), any(), anyString());
    }

    @Test
    void 쿨다운_안에_결과가_있으면_그_카드를_가리키고_모델을_부르지_않는다() {
        given(executionRepository.findTop20ByTeamIdAndCommandOrderByIdDesc(TEAM_ID, SlashCommand.TODO_RECOMMENDATION))
                .willReturn(List.of(doneExecution(RecommendationOutcome.READY, LocalDateTime.now(Clock.fixed(NOW, ZoneId.of("Asia/Seoul"))).minusMinutes(3))));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.COOLDOWN);
        assertThat(result.previousMessageId()).isEqualTo(777L);
        then(openAiClient).should(never()).generateStructured(any(), any(), anyString());
        then(digestBuilder).should(never()).build(any(), any());
    }

    @Test
    void 안내_결과가_앞에_쌓여도_그_뒤에_가려진_직전_카드를_찾아낸다() {
        LocalDateTime now = LocalDateTime.now(Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
        given(executionRepository.findTop20ByTeamIdAndCommandOrderByIdDesc(TEAM_ID, SlashCommand.TODO_RECOMMENDATION))
                .willReturn(List.of(
                        doneExecution(RecommendationOutcome.COOLDOWN, now.minusMinutes(1), 900L),
                        doneExecution(RecommendationOutcome.COOLDOWN, now.minusMinutes(3), 888L),
                        doneExecution(RecommendationOutcome.READY, now.minusMinutes(6), 777L)));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.COOLDOWN);
        assertThat(result.previousMessageId()).isEqualTo(777L);
        then(openAiClient).should(never()).generateStructured(any(), any(), anyString());
    }

    @Test
    void 쿨다운이_지난_결과는_재사용하지_않는다() {
        given(executionRepository.findTop20ByTeamIdAndCommandOrderByIdDesc(TEAM_ID, SlashCommand.TODO_RECOMMENDATION))
                .willReturn(List.of(doneExecution(RecommendationOutcome.READY,
                        LocalDateTime.now(Clock.fixed(NOW, ZoneId.of("Asia/Seoul"))).minusMinutes(11))));
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willReturn(new AiRecommendationResponse("관찰", "인사", List.of()));
        given(sanitizer.sanitize(any(), any())).willReturn(List.of(item()));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.READY);
    }

    @Test
    void 안내만_담긴_직전_결과는_쿨다운_대상이_아니다() {
        given(executionRepository.findTop20ByTeamIdAndCommandOrderByIdDesc(TEAM_ID, SlashCommand.TODO_RECOMMENDATION))
                .willReturn(List.of(doneExecution(RecommendationOutcome.COOLDOWN,
                        LocalDateTime.now(Clock.fixed(NOW, ZoneId.of("Asia/Seoul"))).minusMinutes(1))));
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willReturn(new AiRecommendationResponse("관찰", "인사", List.of()));
        given(sanitizer.sanitize(any(), any())).willReturn(List.of(item()));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.READY);
    }

    @Test
    void rate_limit에_걸리면_COOLDOWN이고_하루_한도는_보지_않는다() {
        given(rateLimiter.tryAcquire(eq("reco:team:" + TEAM_ID), eq(1), any(Duration.class))).willReturn(false);

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.COOLDOWN);
        then(rateLimiter).should(never()).tryAcquire(eq("reco:team:daily:" + TEAM_ID), anyInt(), any());
        then(openAiClient).should(never()).generateStructured(any(), any(), anyString());
    }

    @Test
    void 하루_한도를_넘으면_COOLDOWN이다() {
        given(rateLimiter.tryAcquire(eq("reco:team:" + TEAM_ID), eq(1), any(Duration.class))).willReturn(true);
        given(rateLimiter.tryAcquire(eq("reco:team:daily:" + TEAM_ID), eq(10), any(Duration.class))).willReturn(false);

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.COOLDOWN);
        then(digestBuilder).should(never()).build(any(), any());
    }

    @Test
    void 일시적_실패는_한_번_재시도한다() {
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willThrow(AiClientException.retryable("429", null))
                .willReturn(new AiRecommendationResponse("관찰", "인사", List.of()));
        given(sanitizer.sanitize(any(), any())).willReturn(List.of(item()));

        TeamTodoRecommendationResult result = (TeamTodoRecommendationResult) handler.execute(team, executor);

        assertThat(result.outcome()).isEqualTo(RecommendationOutcome.READY);
        then(openAiClient).should(times(2)).generateStructured(any(), any(), anyString());
    }

    @Test
    void 영구_실패는_재시도하지_않고_전파한다() {
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willThrow(AiClientException.permanent("401", null));

        assertThatThrownBy(() -> handler.execute(team, executor)).isInstanceOf(AiClientException.class);

        then(openAiClient).should(times(1)).generateStructured(any(), any(), anyString());
    }

    @Test
    void 백오프_중_인터럽트가_오면_재시도하지_않고_예외를_전파한다() {
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willAnswer(invocation -> {
                    // 셧다운으로 스레드가 끊긴 상황. 이어지는 Thread.sleep이 즉시 InterruptedException을 던진다.
                    Thread.currentThread().interrupt();
                    throw AiClientException.retryable("429", null);
                });

        try {
            assertThatThrownBy(() -> handler.execute(team, executor)).isInstanceOf(AiClientException.class);
            then(openAiClient).should(times(1)).generateStructured(any(), any(), anyString());
        } finally {
            Thread.interrupted(); // 인터럽트 플래그가 다음 테스트로 새지 않게 정리한다
        }
    }

    @Test
    void 재시도가_또_실패하면_예외를_전파해_인프라가_FAILED로_찍게_한다() {
        givenDigest(RecommendationMode.FULL);
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willThrow(AiClientException.retryable("429", null));

        assertThatThrownBy(() -> handler.execute(team, executor)).isInstanceOf(AiClientException.class);

        then(openAiClient).should(times(2)).generateStructured(any(), any(), anyString());
    }

    @Test
    void 모델_요청에_모드별_프롬프트와_스키마와_출력_상한을_싣는다() {
        givenDigest(RecommendationMode.FULL);
        given(promptProvider.systemInstruction(RecommendationMode.FULL)).willReturn("FULL 프롬프트");
        given(openAiClient.generateStructured(any(), eq(AiRecommendationResponse.class), anyString()))
                .willReturn(new AiRecommendationResponse("관찰", "인사", List.of()));
        given(sanitizer.sanitize(any(), any())).willReturn(List.of(item()));

        handler.execute(team, executor);

        org.mockito.ArgumentCaptor<AiStructuredRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AiStructuredRequest.class);
        then(openAiClient).should().generateStructured(captor.capture(), any(), eq("team-reco-100-2026-08-24"));
        AiStructuredRequest request = captor.getValue();
        assertThat(request.systemInstruction()).isEqualTo("FULL 프롬프트");
        assertThat(request.schemaName()).isEqualTo(RecommendationPromptProvider.SCHEMA_NAME);
        assertThat(request.maxOutputTokens()).isEqualTo(1200);
        assertThat(request.hasImage()).isFalse();
    }

    private void givenDigest(RecommendationMode mode) {
        given(digestBuilder.build(TEAM_ID, TODAY)).willReturn(new TeamActivityDigest(
                mode, "<team_data>x</team_data>", TODAY, 2, Map.of(1L, "민수"), Set.of(10L), List.of()));
    }

    private TeamTodoRecommendationItem item() {
        return TeamTodoRecommendationItem.of(0, RecommendationKind.NEW, "제목", "설명", "근거",
                TODAY.plusDays(1).atTime(21, 0).atOffset(java.time.ZoneOffset.ofHours(9)), null, List.of());
    }

    private SlashCommandExecution doneExecution(RecommendationOutcome outcome, LocalDateTime executedAt) {
        return doneExecution(outcome, executedAt, 777L);
    }

    private SlashCommandExecution doneExecution(RecommendationOutcome outcome, LocalDateTime executedAt, Long messageId) {
        TeamChatMessage message = TeamChatMessage.create(team, executor, "/할일추천");
        ReflectionTestUtils.setField(message, "id", messageId);
        SlashCommandExecution execution = SlashCommandExecution.createPending(
                team, executor, message, SlashCommand.TODO_RECOMMENDATION);
        String json = switch (outcome) {
            case COOLDOWN -> "{\"outcome\":\"COOLDOWN\",\"greeting\":\"g\",\"previousMessageId\":1,\"items\":[]}";
            case UNAVAILABLE -> "{\"outcome\":\"UNAVAILABLE\",\"greeting\":\"g\",\"previousMessageId\":null,\"items\":[]}";
            default -> "{\"outcome\":\"READY\",\"greeting\":\"g\",\"previousMessageId\":null,\"items\":[]}";
        };
        execution.complete(json, executedAt);
        return execution;
    }
}
