package com.todo.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.SlashCommandDispatchService;
import com.todo.domain.chat.command.dto.response.SlashCommandResultResponse;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.request.MarkAsReadRequest;
import com.todo.domain.chat.dto.request.TypingStatusRequest;
import com.todo.domain.chat.dto.response.ChatUnreadCountResponse;
import com.todo.domain.chat.dto.response.TeamChatMessagePageResponse;
import com.todo.domain.chat.dto.response.TeamChatMessageResponse;
import com.todo.domain.chat.dto.response.TypingStatusResponse;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.chat.entity.TeamChatReadStatus;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TeamChatServiceTest {

    @InjectMocks
    private TeamChatService teamChatService;

    @Mock private TeamChatMessageRepository teamChatMessageRepository;
    @Mock private TeamChatReadStatusRepository teamChatReadStatusRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationMessageFactory notificationMessageFactory;
    @Mock private SlashCommandDispatchService slashCommandDispatchService;
    @Mock private SlashCommandExecutionRepository slashCommandExecutionRepository;
    @Mock private ObjectMapper objectMapper;

    @Test
    void 메시지_저장_성공() {
        User sender = userWithId(1L);
        Team team = teamWithId(100L);
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(teamRepository.getReferenceById(100L)).willReturn(team);
        given(teamChatMessageRepository.save(any(TeamChatMessage.class))).willAnswer(invocation -> {
            TeamChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 1000L);
            ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 6, 4, 12, 0));
            return message;
        });

        TeamChatMessageResponse response = teamChatService.saveMessage(100L, "1", new ChatMessageRequest("안녕"));

        assertThat(response.messageId()).isEqualTo(1000L);
        assertThat(response.teamId()).isEqualTo(100L);
        assertThat(response.senderId()).isEqualTo(1L);
        assertThat(response.senderNickname()).isEqualTo("닉네임1");
        assertThat(response.content()).isEqualTo("안녕");
        assertThat(response.isBot()).isFalse();
    }

    @Test
    void 메시지_저장은_사용자가_없으면_401_예외를_던진다() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamChatService.saveMessage(100L, "999", new ChatMessageRequest("안녕")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용자를 찾을 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 메시지_저장은_팀원이_아니면_403_예외를_던진다() {
        User sender = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamChatService.saveMessage(100L, "1", new ChatMessageRequest("안녕")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("채팅에 참여할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        then(teamChatMessageRepository).should(never()).save(any());
    }

    @Test
    void 메시지_저장시_다른_팀원에게_알림을_저장없이_푸시한다() {
        User sender = userWithId(1L);
        User receiver = userWithId(2L);
        Team team = teamWithId(100L);
        TeamMember member = TeamMember.create(team, receiver, TeamMemberRole.MEMBER);
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(teamRepository.getReferenceById(100L)).willReturn(team);
        given(teamMemberRepository.findByTeamIdExcludingUser(100L, 1L)).willReturn(List.of(member));
        given(teamChatMessageRepository.save(any(TeamChatMessage.class))).willAnswer(invocation -> {
            TeamChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 1000L);
            ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 6, 4, 12, 0));
            return message;
        });

        NotificationMessage message = new NotificationMessage(
                NotificationType.CHAT_MESSAGE, "닉네임1님이 메시지를 보냈습니다.", "안녕");
        given(notificationMessageFactory.chatMessage(sender.getNickname(), "안녕")).willReturn(message);

        teamChatService.saveMessage(100L, "1", new ChatMessageRequest("안녕"));

        then(notificationService).should().pushAll(eq(List.of(receiver)), eq(message), eq(100L), eq(100L));
        then(notificationService).should(never()).sendAll(any(), any(), any(), any(), any());
    }

    @Test
    void 슬래시_명령어와_일치하는_메시지는_디스패치를_호출한다() {
        User sender = userWithId(1L);
        Team team = teamWithId(100L);
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(teamRepository.getReferenceById(100L)).willReturn(team);
        given(teamChatMessageRepository.save(any(TeamChatMessage.class))).willAnswer(invocation -> {
            TeamChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 1000L);
            ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 6, 4, 12, 0));
            return message;
        });
        given(slashCommandDispatchService.match("/내할일")).willReturn(Optional.of(SlashCommand.MY_TODOS));

        teamChatService.saveMessage(100L, "1", new ChatMessageRequest("/내할일"));

        then(slashCommandDispatchService).should().dispatchIfHandled(
                eq(team), eq(sender), any(TeamChatMessage.class), eq(SlashCommand.MY_TODOS));
    }

    @Test
    void 일반_메시지는_디스패치를_호출하지_않는다() {
        User sender = userWithId(1L);
        Team team = teamWithId(100L);
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(teamRepository.getReferenceById(100L)).willReturn(team);
        given(teamChatMessageRepository.save(any(TeamChatMessage.class))).willAnswer(invocation -> {
            TeamChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 1000L);
            ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 6, 4, 12, 0));
            return message;
        });

        teamChatService.saveMessage(100L, "1", new ChatMessageRequest("안녕"));

        then(slashCommandDispatchService).should(never())
                .dispatchIfHandled(any(), any(), any(), any());
    }

    @Test
    void 명령어_결과_조회는_DONE_상태의_결과를_반환한다() throws Exception {
        User user = userWithId(1L);
        givenTeamMember(user, 100L);
        SlashCommandExecution execution = doneExecution(SlashCommand.MY_TODOS, user);
        given(slashCommandExecutionRepository.findByChatMessageIdAndTeamId(1000L, 100L)).willReturn(Optional.of(execution));
        given(objectMapper.readTree("{\"count\":1}")).willReturn(TextNode.valueOf("stub"));

        SlashCommandResultResponse response = teamChatService.getCommandResult(100L, "1", 1000L);

        assertThat(response.command()).isEqualTo("MY_TODOS");
        assertThat(response.status()).isEqualTo("DONE");
    }

    @Test
    void 명령어_결과_조회는_처리중이면_PENDING을_반환한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        givenTeamMember(user, 100L);
        TeamChatMessage triggerMessage = messageWithId(1000L, team, user, "/내할일");
        SlashCommandExecution execution = SlashCommandExecution.createPending(team, user, triggerMessage, SlashCommand.MY_TODOS);
        given(slashCommandExecutionRepository.findByChatMessageIdAndTeamId(1000L, 100L)).willReturn(Optional.of(execution));

        SlashCommandResultResponse response = teamChatService.getCommandResult(100L, "1", 1000L);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.result()).isNull();
        assertThat(response.executedAt()).isNull();
    }

    @Test
    void 개인용_명령어는_실행자_본인_아니면_403() {
        User executor = userWithId(1L);
        User other = userWithId(2L);
        givenTeamMember(other, 100L);
        SlashCommandExecution execution = doneExecution(SlashCommand.MY_TODOS, executor);
        given(slashCommandExecutionRepository.findByChatMessageIdAndTeamId(1000L, 100L)).willReturn(Optional.of(execution));

        assertThatThrownBy(() -> teamChatService.getCommandResult(100L, "2", 1000L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 실행한 명령어만 조회할 수 있습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 팀용_명령어는_팀원이면_누구나_조회한다() throws Exception {
        User executor = userWithId(1L);
        User other = userWithId(2L);
        givenTeamMember(other, 100L);
        SlashCommandExecution execution = doneExecution(SlashCommand.TEAM_STATUS, executor);
        given(slashCommandExecutionRepository.findByChatMessageIdAndTeamId(1000L, 100L)).willReturn(Optional.of(execution));
        given(objectMapper.readTree("{\"count\":1}")).willReturn(TextNode.valueOf("stub"));

        SlashCommandResultResponse response = teamChatService.getCommandResult(100L, "2", 1000L);

        assertThat(response.status()).isEqualTo("DONE");
    }

    @Test
    void 다른_팀의_messageId로는_결과를_조회할_수_없다() {
        User user = userWithId(1L);
        givenTeamMember(user, 100L);
        given(slashCommandExecutionRepository.findByChatMessageIdAndTeamId(2000L, 100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamChatService.getCommandResult(100L, "1", 2000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        then(slashCommandExecutionRepository).should(never()).findByChatMessageIdAndTeamId(2000L, 200L);
    }

    @Test
    void 명령어_실행_결과가_없으면_404() {
        User user = userWithId(1L);
        givenTeamMember(user, 100L);
        given(slashCommandExecutionRepository.findByChatMessageIdAndTeamId(1000L, 100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamChatService.getCommandResult(100L, "1", 1000L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("명령어 실행 결과가 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 메시지_목록은_커서가_없으면_최신_메시지를_조회한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        TeamChatMessage first = messageWithId(100L, team, user, "첫번째");
        TeamChatMessage second = messageWithId(99L, team, user, "두번째");
        TeamChatMessage extra = messageWithId(98L, team, user, "추가");
        givenTeamMember(user, 100L);
        given(teamChatMessageRepository.findLatestMessages(100L, PageRequest.of(0, 3)))
                .willReturn(List.of(first, second, extra));

        TeamChatMessagePageResponse response = teamChatService.getMessages(100L, "1", null, 2);

        assertThat(response.messages()).extracting(TeamChatMessageResponse::messageId).containsExactly(100L, 99L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(99L);
    }

    @Test
    void 메시지_목록은_커서가_있으면_커서_이전_메시지를_조회한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        TeamChatMessage message = messageWithId(90L, team, user, "이전");
        givenTeamMember(user, 100L);
        given(teamChatMessageRepository.findMessagesByCursor(100L, 99L, PageRequest.of(0, 3)))
                .willReturn(List.of(message));

        TeamChatMessagePageResponse response = teamChatService.getMessages(100L, "1", 99L, 2);

        assertThat(response.messages()).extracting(TeamChatMessageResponse::messageId).containsExactly(90L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursorId()).isNull();
    }

    @Test
    void 메시지_목록은_팀원이_아니면_403_예외를_던진다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamChatService.getMessages(100L, "1", null, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("채팅에 참여할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 타이핑_상태를_반환한다() {
        User user = userWithId(1L);
        givenTeamMember(user, 100L);

        TypingStatusResponse response = teamChatService.handleTyping(100L, "1", new TypingStatusRequest(true));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("닉네임1");
        assertThat(response.isTyping()).isTrue();
    }

    @Test
    void 타이핑은_팀원이_아니면_403_예외를_던진다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamChatService.handleTyping(100L, "1", new TypingStatusRequest(true)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("채팅에 참여할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 읽음처리는_기존_읽음상태를_갱신한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        TeamChatReadStatus readStatus = TeamChatReadStatus.create(team, user);
        givenTeamMemberWithTeam(user, team);
        given(teamChatReadStatusRepository.findByUserIdAndTeamId(1L, 100L)).willReturn(Optional.of(readStatus));

        teamChatService.markAsRead(100L, "1", new MarkAsReadRequest(500L));

        assertThat(readStatus.getLastReadMessageId()).isEqualTo(500L);
        then(teamChatReadStatusRepository).should(never()).save(any());
    }

    @Test
    void 읽음처리는_읽음상태가_없으면_새로_생성한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        givenTeamMemberWithTeam(user, team);
        given(teamChatReadStatusRepository.findByUserIdAndTeamId(1L, 100L)).willReturn(Optional.empty());
        given(teamChatReadStatusRepository.save(any(TeamChatReadStatus.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        teamChatService.markAsRead(100L, "1", new MarkAsReadRequest(500L));

        then(teamChatReadStatusRepository).should().save(any(TeamChatReadStatus.class));
    }

    @Test
    void 읽음처리는_더_작은_메시지ID로는_갱신하지_않는다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        TeamChatReadStatus readStatus = TeamChatReadStatus.create(team, user);
        readStatus.updateLastReadMessageId(500L);
        givenTeamMemberWithTeam(user, team);
        given(teamChatReadStatusRepository.findByUserIdAndTeamId(1L, 100L)).willReturn(Optional.of(readStatus));

        teamChatService.markAsRead(100L, "1", new MarkAsReadRequest(300L));

        assertThat(readStatus.getLastReadMessageId()).isEqualTo(500L);
    }

    @Test
    void 읽음처리는_팀원이_아니면_403_예외를_던진다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamChatService.markAsRead(100L, "1", new MarkAsReadRequest(500L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("채팅에 참여할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 안읽은_수는_lastRead가_없으면_전체_메시지_수를_반환한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        TeamChatReadStatus readStatus = TeamChatReadStatus.create(team, user);
        givenTeamMember(user, 100L);
        given(teamChatReadStatusRepository.findByUserIdAndTeamId(1L, 100L)).willReturn(Optional.of(readStatus));
        given(teamChatReadStatusRepository.countAllMessages(100L)).willReturn(7L);

        ChatUnreadCountResponse response = teamChatService.getUnreadCount(100L, "1");

        assertThat(response.teamId()).isEqualTo(100L);
        assertThat(response.unreadCount()).isEqualTo(7L);
    }

    @Test
    void 안읽은_수는_lastRead_이후_메시지_수를_반환한다() {
        User user = userWithId(1L);
        Team team = teamWithId(100L);
        TeamChatReadStatus readStatus = TeamChatReadStatus.create(team, user);
        readStatus.updateLastReadMessageId(500L);
        givenTeamMember(user, 100L);
        given(teamChatReadStatusRepository.findByUserIdAndTeamId(1L, 100L)).willReturn(Optional.of(readStatus));
        given(teamChatReadStatusRepository.countUnreadMessages(100L, 500L)).willReturn(3L);

        ChatUnreadCountResponse response = teamChatService.getUnreadCount(100L, "1");

        assertThat(response.unreadCount()).isEqualTo(3L);
    }

    @Test
    void 안읽은_수는_읽음상태가_없으면_전체_메시지_수를_반환한다() {
        User user = userWithId(1L);
        givenTeamMember(user, 100L);
        given(teamChatReadStatusRepository.findByUserIdAndTeamId(1L, 100L)).willReturn(Optional.empty());
        given(teamChatReadStatusRepository.countAllMessages(100L)).willReturn(5L);

        ChatUnreadCountResponse response = teamChatService.getUnreadCount(100L, "1");

        assertThat(response.unreadCount()).isEqualTo(5L);
    }

    @Test
    void 안읽은_수는_팀원이_아니면_403_예외를_던진다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> teamChatService.getUnreadCount(100L, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("채팅에 참여할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void givenTeamMember(User user, Long teamId) {
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())).willReturn(true);
    }

    private void givenTeamMemberWithTeam(User user, Team team) {
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(team.getId(), user.getId())).willReturn(true);
        given(teamRepository.getReferenceById(team.getId())).willReturn(team);
    }

    private SlashCommandExecution doneExecution(SlashCommand command, User executor) {
        Team team = teamWithId(100L);
        TeamChatMessage triggerMessage = messageWithId(1000L, team, executor, command.commandText());
        SlashCommandExecution execution =
                SlashCommandExecution.createPending(team, executor, triggerMessage, command);
        execution.complete("{\"count\":1}", LocalDateTime.of(2026, 6, 4, 12, 5));
        return execution;
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임" + id, "profiles/" + id + ".png");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Team teamWithId(Long id) {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private TeamChatMessage messageWithId(Long id, Team team, User sender, String content) {
        TeamChatMessage message = TeamChatMessage.create(team, sender, content);
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 6, 4, 12, 0));
        return message;
    }
}
