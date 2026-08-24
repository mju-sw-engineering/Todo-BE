package com.todo.domain.todo.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationItem;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationResult;
import com.todo.domain.todo.service.TodoService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TeamTodoRecommendationRegisterServiceTest {

    private static final Long TEAM_ID = 100L;
    private static final Long MESSAGE_ID = 777L;
    private static final OffsetDateTime DEADLINE =
            OffsetDateTime.of(2026, 8, 26, 21, 0, 0, 0, ZoneOffset.ofHours(9));
    /** 2026-08-24(월) 10:00 KST. DEADLINE(8/26)은 미래, 지난 마감 테스트는 이 시점을 기준으로 본다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T01:00:00Z"), ZoneOffset.UTC);

    @Mock private SlashCommandExecutionRepository executionRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private TodoService todoService;

    private TeamTodoRecommendationRegisterService service;
    private ObjectMapper objectMapper;
    private Team team;
    private User registrant;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new TeamTodoRecommendationRegisterService(
                executionRepository, teamMemberRepository, userRepository, todoService, objectMapper, CLOCK);
        team = Team.create("팀", "설명", null, "INVITE01");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        registrant = User.create("user", "pw", "민수", null);
        ReflectionTestUtils.setField(registrant, "id", 1L);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(registrant));
        lenient().when(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).thenReturn(true);
    }

    @Test
    void 추천_항목을_투두로_등록하고_결과에_등록자를_남긴다() {
        SlashCommandExecution execution = execution(result(item(0, List.of(2L))));
        givenLockedExecution(execution);
        given(todoService.createTodo(eq("1"), eq(TEAM_ID), any())).willReturn(createdTodo(500L));

        CreateTodoResponse response = service.register(TEAM_ID, "1", MESSAGE_ID, 0);

        assertThat(response.todoId()).isEqualTo(500L);
        ArgumentCaptor<CreateTodoRequest> captor = ArgumentCaptor.forClass(CreateTodoRequest.class);
        then(todoService).should().createTodo(eq("1"), eq(TEAM_ID), captor.capture());
        CreateTodoRequest request = captor.getValue();
        assertThat(request.title()).isEqualTo("발표자료 개요");
        assertThat(request.deadline()).isEqualTo(DEADLINE);
        assertThat(request.assigneeIds()).containsExactly(2L);
        assertThat(request.tasks()).isNull();

        TeamTodoRecommendationItem saved = readSaved(execution).items().get(0);
        assertThat(saved.registeredTodoId()).isEqualTo(500L);
        assertThat(saved.registeredBy()).isEqualTo(1L);
        assertThat(saved.registeredByNickname()).isEqualTo("민수");
    }

    @Test
    void 담당자_추천이_없으면_등록자_본인이_담당한다() {
        givenLockedExecution(execution(result(item(0, List.of()))));
        given(todoService.createTodo(anyString(), anyLong(), any())).willReturn(createdTodo(500L));

        service.register(TEAM_ID, "1", MESSAGE_ID, 0);

        ArgumentCaptor<CreateTodoRequest> captor = ArgumentCaptor.forClass(CreateTodoRequest.class);
        then(todoService).should().createTodo(anyString(), anyLong(), captor.capture());
        assertThat(captor.getValue().assigneeIds()).containsExactly(1L);
    }

    @Test
    void 마감이_지난_카드는_다음_평일로_옮겨_등록한다() {
        TeamTodoRecommendationItem stale = TeamTodoRecommendationItem.of(
                0, RecommendationKind.NEW, "발표자료 개요", "설명", "근거",
                OffsetDateTime.of(2026, 8, 20, 21, 0, 0, 0, ZoneOffset.ofHours(9)), null, List.of());
        givenLockedExecution(execution(result(stale)));
        given(todoService.createTodo(anyString(), anyLong(), any())).willReturn(createdTodo(500L));

        service.register(TEAM_ID, "1", MESSAGE_ID, 0);

        ArgumentCaptor<CreateTodoRequest> captor = ArgumentCaptor.forClass(CreateTodoRequest.class);
        then(todoService).should().createTodo(anyString(), anyLong(), captor.capture());
        assertThat(captor.getValue().deadline())
                .isEqualTo(OffsetDateTime.of(2026, 8, 25, 21, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void 이미_등록된_항목이면_409이고_투두를_만들지_않는다() {
        TeamTodoRecommendationItem registered = item(0, List.of()).withRegistration(400L, 2L, "유나");
        givenLockedExecution(execution(result(registered)));

        assertThatThrownBy(() -> service.register(TEAM_ID, "1", MESSAGE_ID, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        then(todoService).should(never()).createTodo(anyString(), anyLong(), any());
    }

    @Test
    void 팀원이_아니면_403이고_실행_행을_조회하지도_않는다() {
        given(teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, 1L)).willReturn(false);

        assertThatThrownBy(() -> service.register(TEAM_ID, "1", MESSAGE_ID, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        then(executionRepository).should(never()).findByChatMessageIdAndTeamIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void 다른_명령어의_결과이면_404다() {
        SlashCommandExecution other = SlashCommandExecution.createPending(
                team, registrant, message(), SlashCommand.TEAM_STATUS);
        other.complete("{}", java.time.LocalDateTime.now());
        givenLockedExecution(other);

        assertThatThrownBy(() -> service.register(TEAM_ID, "1", MESSAGE_ID, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 아직_처리_중인_결과이면_404다() {
        SlashCommandExecution pending = SlashCommandExecution.createPending(
                team, registrant, message(), SlashCommand.TODO_RECOMMENDATION);
        givenLockedExecution(pending);

        assertThatThrownBy(() -> service.register(TEAM_ID, "1", MESSAGE_ID, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 없는_index는_404다() {
        givenLockedExecution(execution(result(item(0, List.of()))));

        assertThatThrownBy(() -> service.register(TEAM_ID, "1", MESSAGE_ID, 5))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.register(TEAM_ID, "1", MESSAGE_ID, -1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 실행_행이_없으면_404다() {
        given(executionRepository.findByChatMessageIdAndTeamIdForUpdate(MESSAGE_ID, TEAM_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(TEAM_ID, "1", MESSAGE_ID, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void givenLockedExecution(SlashCommandExecution execution) {
        given(executionRepository.findByChatMessageIdAndTeamIdForUpdate(MESSAGE_ID, TEAM_ID))
                .willReturn(Optional.of(execution));
    }

    private TeamTodoRecommendationResult readSaved(SlashCommandExecution execution) {
        try {
            return objectMapper.readValue(execution.getResultJson(), TeamTodoRecommendationResult.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TeamTodoRecommendationItem item(int index, List<Long> assignees) {
        return TeamTodoRecommendationItem.of(index, RecommendationKind.SPLIT, "발표자료 개요",
                "첫 단계만 떼어냈어요", "'발표자료'가 두 번 마감을 넘겼어요", DEADLINE, 10L, assignees);
    }

    private TeamTodoRecommendationResult result(TeamTodoRecommendationItem item) {
        return TeamTodoRecommendationResult.ready("이렇게 해봐요", List.of(item));
    }

    private SlashCommandExecution execution(TeamTodoRecommendationResult result) {
        SlashCommandExecution execution = SlashCommandExecution.createPending(
                team, registrant, message(), SlashCommand.TODO_RECOMMENDATION);
        try {
            execution.complete(objectMapper.writeValueAsString(result), java.time.LocalDateTime.now());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return execution;
    }

    private TeamChatMessage message() {
        TeamChatMessage message = TeamChatMessage.create(team, registrant, "/할일추천");
        ReflectionTestUtils.setField(message, "id", MESSAGE_ID);
        return message;
    }

    private CreateTodoResponse createdTodo(Long todoId) {
        return new CreateTodoResponse(todoId, TodoMode.DIRECT, "발표자료 개요", DEADLINE,
                TodoStatus.IN_PROGRESS, List.of(), List.of());
    }
}
