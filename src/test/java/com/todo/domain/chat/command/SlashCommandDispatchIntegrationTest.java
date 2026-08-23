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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메시지 저장 트랜잭션이 실제로 커밋된 뒤 AFTER_COMMIT 리스너가 핸들러를 실행하고
 * 그 결과(DONE)가 DB에 남는지 확인한다.
 *
 * <p>단위 테스트(mock)로는 잡히지 않는 회귀를 막는다 — AFTER_COMMIT 시점에 REQUIRED로
 * 열린 트랜잭션은 이미 커밋된 원래 트랜잭션에 참여해 두 번째 커밋이 없고, 결과가 영원히
 * PENDING에 남는다. 테스트 자체에 @Transactional을 붙이지 않아야 커밋 이벤트가 발화한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SlashCommandDispatchIntegrationTest {

    @Autowired private TeamChatService teamChatService;
    @Autowired private SlashCommandExecutionRepository executionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void 팀현황_명령어를_저장하면_커밋_후_실행_결과가_DONE으로_남는다() {
        Long[] ids = transactionTemplate.execute(status -> {
            User user = userRepository.save(User.create("cmd-user", "pw", "명령어사용자", null));
            Team team = teamRepository.save(Team.create("팀", null, "CMD00001"));
            teamMemberRepository.save(TeamMember.create(team, user, TeamMemberRole.LEADER));
            return new Long[]{team.getId(), user.getId()};
        });

        TeamChatMessageResponse message = teamChatService.saveMessage(
                ids[0], ids[1].toString(), new ChatMessageRequest("/팀현황"));

        SlashCommandExecution execution = executionRepository
                .findByChatMessageIdAndTeamId(message.messageId(), ids[0])
                .orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.DONE);
        assertThat(execution.getResultJson()).isNotNull();
    }
}
