package com.todo.domain.chat.command;

import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.entity.SlashCommandExecutionStatus;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.response.TeamChatMessageResponse;
import com.todo.domain.chat.service.TeamChatService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메시지 저장 트랜잭션이 실제로 커밋된 뒤 비동기 스레드에서 핸들러가 실행되고 그 결과가
 * DB에 남는지 확인한다.
 *
 * <p>단위 테스트(mock)로는 잡히지 않는 회귀를 막는다 — 과거에 AFTER_COMMIT 리스너 안에서
 * REQUIRED로 연 트랜잭션이 이미 커밋된 원래 트랜잭션에 참여해 두 번째 커밋이 없었고, 결과가
 * 영원히 PENDING에 남았다. 테스트 자체에 @Transactional을 붙이지 않아야 커밋 이벤트가 발화한다.
 *
 * <p>핸들러는 별도 스레드({@code slashCommandTaskExecutor})에서 돌므로 결과를 폴링으로 기다린다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SlashCommandDispatchIntegrationTest {

    private static final Duration WAIT_LIMIT = Duration.ofSeconds(5);

    /**
     * 실패 경로용 핸들러. 아직 구현체가 없는 {@code MY_TODOS}에 붙여 운영 핸들러와 충돌하지 않는다.
     * 레지스트리는 명령어당 핸들러 하나만 허용하므로, MY_TODOS 구현체가 생기면 이 테스트의
     * 명령어를 바꿔야 한다.
     */
    @TestConfiguration
    static class FailingHandlerConfig {
        static final AtomicInteger CALLS = new AtomicInteger();

        @Bean
        SlashCommandHandler alwaysFailingHandler() {
            return new SlashCommandHandler() {
                @Override
                public SlashCommand command() {
                    return SlashCommand.MY_TODOS;
                }

                @Override
                public Object execute(Team team, User executor) {
                    CALLS.incrementAndGet();
                    throw new IllegalStateException("의도된 실패");
                }
            };
        }
    }

    @Autowired private TeamChatService teamChatService;
    @Autowired private SlashCommandExecutionRepository executionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long teamId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Long[] ids = transactionTemplate.execute(status -> {
            String suffix = String.valueOf(System.nanoTime());
            User user = userRepository.save(User.create("cmd-" + suffix, "pw", "명령어사용자", null));
            Team team = teamRepository.save(Team.create("팀", null, "C" + suffix.substring(suffix.length() - 7)));
            teamMemberRepository.save(TeamMember.create(team, user, TeamMemberRole.LEADER));
            return new Long[]{team.getId(), user.getId()};
        });
        teamId = ids[0];
        userId = ids[1];
    }

    @Test
    void 팀현황_명령어를_저장하면_커밋_후_비동기로_실행되고_DONE으로_남는다() {
        TeamChatMessageResponse message = teamChatService.saveMessage(
                teamId, userId.toString(), new ChatMessageRequest("/팀현황"));

        SlashCommandExecution execution = awaitExecution(message.messageId(),
                e -> e.getStatus() != SlashCommandExecutionStatus.PENDING);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.DONE);
        assertThat(execution.getResultJson()).contains("inProgressCount");
        assertThat(execution.getExecutedAt()).isNotNull();
    }

    @Test
    void 핸들러가_예외를_던지면_FAILED로_확정된다() {
        TeamChatMessageResponse message = teamChatService.saveMessage(
                teamId, userId.toString(), new ChatMessageRequest("/내할일"));

        SlashCommandExecution execution = awaitExecution(message.messageId(),
                e -> e.getStatus() != SlashCommandExecutionStatus.PENDING);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.FAILED);
        assertThat(execution.getResultJson()).isNull();
        assertThat(FailingHandlerConfig.CALLS.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 앞뒤_공백이_붙은_명령어도_인식한다() {
        TeamChatMessageResponse message = teamChatService.saveMessage(
                teamId, userId.toString(), new ChatMessageRequest(" /팀현황 "));

        SlashCommandExecution execution = awaitExecution(message.messageId(),
                e -> e.getStatus() != SlashCommandExecutionStatus.PENDING);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.DONE);
    }

    private SlashCommandExecution awaitExecution(Long messageId, Predicate<SlashCommandExecution> done) {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        while (true) {
            SlashCommandExecution execution = executionRepository
                    .findByChatMessageIdAndTeamId(messageId, teamId)
                    .orElseThrow();
            if (done.test(execution) || System.nanoTime() > deadline) {
                return execution;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return execution;
            }
        }
    }
}
