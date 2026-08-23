package com.todo.domain.chat.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.entity.SlashCommandExecutionStatus;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SlashCommandDispatchServiceTest {

    @InjectMocks
    private SlashCommandDispatchService dispatchService;

    @Mock private SlashCommandRegistry registry;
    @Mock private SlashCommandExecutionRepository executionRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationMessageFactory notificationMessageFactory;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ObjectMapper objectMapper;

    @Test
    void 명령어_텍스트와_일치하면_매칭된다() {
        assertThat(dispatchService.match("/내할일")).contains(SlashCommand.MY_TODOS);
        assertThat(dispatchService.match("/내할일 ")).contains(SlashCommand.MY_TODOS);
        assertThat(dispatchService.match("  /내할일")).contains(SlashCommand.MY_TODOS);
        assertThat(dispatchService.match("/내할일요")).isEmpty();
        assertThat(dispatchService.match(null)).isEmpty();
        assertThat(dispatchService.match("안녕")).isEmpty();
    }

    @Test
    void 핸들러가_등록돼_있으면_PENDING_실행을_저장하고_이벤트를_발행한다() {
        Team team = teamWithId(100L);
        User executor = userWithId(1L);
        TeamChatMessage message = messageWithId(1000L);
        given(registry.findHandler(SlashCommand.MY_TODOS)).willReturn(Optional.of(stubHandler()));
        given(executionRepository.save(any(SlashCommandExecution.class))).willAnswer(invocation -> {
            SlashCommandExecution execution = invocation.getArgument(0);
            ReflectionTestUtils.setField(execution, "id", 5000L);
            return execution;
        });

        dispatchService.dispatchIfHandled(team, executor, message, SlashCommand.MY_TODOS);

        then(executionRepository).should().save(any(SlashCommandExecution.class));
        then(eventPublisher).should().publishEvent(new SlashCommandDispatchEvent(
                5000L, 100L, 1L, 1000L, SlashCommand.MY_TODOS));
    }

    @Test
    void 핸들러가_없으면_아무_것도_저장하지_않는다() {
        Team team = teamWithId(100L);
        User executor = userWithId(1L);
        TeamChatMessage message = messageWithId(1000L);
        given(registry.findHandler(SlashCommand.MY_TODOS)).willReturn(Optional.empty());

        dispatchService.dispatchIfHandled(team, executor, message, SlashCommand.MY_TODOS);

        then(executionRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    void 개인용_명령어_완료시_실행자에게만_알림을_보낸다() throws Exception {
        SlashCommandExecution execution = pendingExecution();
        SlashCommandDispatchEvent event = new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.MY_TODOS);
        Team team = teamWithId(100L);
        User executor = userWithId(1L);
        given(registry.findHandler(SlashCommand.MY_TODOS)).willReturn(Optional.of(stubHandler()));
        given(executionRepository.findById(5000L)).willReturn(Optional.of(execution));
        given(teamRepository.getReferenceById(100L)).willReturn(team);
        given(userRepository.getReferenceById(1L)).willReturn(executor);
        given(objectMapper.writeValueAsString(any())).willReturn("{\"count\":1}");
        NotificationMessage message = new NotificationMessage(NotificationType.SLASH_COMMAND_RESULT, "제목", "본문");
        given(notificationMessageFactory.slashCommandResult("/내할일")).willReturn(message);

        dispatchService.executeAndComplete(event);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.DONE);
        assertThat(execution.getResultJson()).isEqualTo("{\"count\":1}");
        then(notificationService).should().pushAll(eq(List.of(executor)), eq(message), eq(1000L), eq(100L));
        then(teamMemberRepository).should(never()).findByTeamIdWithUser(any());
    }

    @Test
    void 팀용_명령어_완료시_팀원_전원에게_알림을_보낸다() throws Exception {
        SlashCommandExecution execution = pendingExecution();
        SlashCommandDispatchEvent event =
                new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.TEAM_STATUS);
        Team team = teamWithId(100L);
        User executor = userWithId(1L);
        User other = userWithId(2L);
        given(registry.findHandler(SlashCommand.TEAM_STATUS)).willReturn(Optional.of(stubHandler()));
        given(executionRepository.findById(5000L)).willReturn(Optional.of(execution));
        given(teamRepository.getReferenceById(100L)).willReturn(team);
        given(userRepository.getReferenceById(1L)).willReturn(executor);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(teamMemberRepository.findByTeamIdWithUser(100L)).willReturn(List.of(
                TeamMember.create(team, executor, TeamMemberRole.LEADER),
                TeamMember.create(team, other, TeamMemberRole.MEMBER)));
        NotificationMessage message = new NotificationMessage(NotificationType.SLASH_COMMAND_RESULT, "제목", "본문");
        given(notificationMessageFactory.slashCommandResult("/팀현황")).willReturn(message);

        dispatchService.executeAndComplete(event);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.DONE);
        then(notificationService).should().pushAll(eq(List.of(executor, other)), eq(message), eq(1000L), eq(100L));
    }

    @Test
    void 완료_시점에_등록된_핸들러가_없으면_조용히_넘어간다() {
        SlashCommandDispatchEvent event = new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.MY_TODOS);
        given(registry.findHandler(SlashCommand.MY_TODOS)).willReturn(Optional.empty());

        dispatchService.executeAndComplete(event);

        then(executionRepository).should(never()).findById(any());
        then(notificationService).should(never()).pushAll(any(), any(), any(), any());
    }

    @Test
    void 핸들러_실행중_예외가_나면_그대로_전파하고_상태를_바꾸지_않는다() {
        SlashCommandExecution execution = pendingExecution();
        SlashCommandDispatchEvent event = new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.MY_TODOS);
        given(registry.findHandler(SlashCommand.MY_TODOS)).willReturn(Optional.of(failingHandler()));
        given(executionRepository.findById(5000L)).willReturn(Optional.of(execution));
        given(teamRepository.getReferenceById(100L)).willReturn(teamWithId(100L));
        given(userRepository.getReferenceById(1L)).willReturn(userWithId(1L));

        assertThatThrownBy(() -> dispatchService.executeAndComplete(event))
                .isInstanceOf(IllegalStateException.class);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.PENDING);
        then(notificationService).should(never()).pushAll(any(), any(), any(), any());
    }

    @Test
    void markFailed는_FAILED로_확정하고_알림을_보낸다() {
        SlashCommandExecution execution = pendingExecution();
        SlashCommandDispatchEvent event = new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.MY_TODOS);
        User executor = userWithId(1L);
        given(executionRepository.findById(5000L)).willReturn(Optional.of(execution));
        given(userRepository.getReferenceById(1L)).willReturn(executor);
        NotificationMessage message = new NotificationMessage(NotificationType.SLASH_COMMAND_RESULT, "제목", "본문");
        given(notificationMessageFactory.slashCommandResult("/내할일")).willReturn(message);

        dispatchService.markFailed(event);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.FAILED);
        assertThat(execution.getExecutedAt()).isNotNull();
        then(notificationService).should().pushAll(eq(List.of(executor)), eq(message), eq(1000L), eq(100L));
    }

    @Test
    void markFailed는_실행_행이_없으면_조용히_넘어간다() {
        SlashCommandDispatchEvent event = new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.MY_TODOS);
        given(executionRepository.findById(5000L)).willReturn(Optional.empty());

        dispatchService.markFailed(event);

        then(notificationService).should(never()).pushAll(any(), any(), any(), any());
    }

    private SlashCommandHandler stubHandler() {
        return new SlashCommandHandler() {
            @Override
            public SlashCommand command() {
                return SlashCommand.MY_TODOS;
            }

            @Override
            public Object execute(Team team, User executor) {
                return Map.of("count", 1);
            }
        };
    }

    private SlashCommandHandler failingHandler() {
        return new SlashCommandHandler() {
            @Override
            public SlashCommand command() {
                return SlashCommand.MY_TODOS;
            }

            @Override
            public Object execute(Team team, User executor) {
                throw new IllegalStateException("의도된 실패");
            }
        };
    }

    private SlashCommandExecution pendingExecution() {
        Team team = teamWithId(100L);
        User executor = userWithId(1L);
        TeamChatMessage message = messageWithId(1000L);
        SlashCommandExecution execution =
                SlashCommandExecution.createPending(team, executor, message, SlashCommand.MY_TODOS);
        ReflectionTestUtils.setField(execution, "id", 5000L);
        return execution;
    }

    private Team teamWithId(Long id) {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임" + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private TeamChatMessage messageWithId(Long id) {
        TeamChatMessage message = TeamChatMessage.create(teamWithId(100L), userWithId(1L), "/내할일");
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
