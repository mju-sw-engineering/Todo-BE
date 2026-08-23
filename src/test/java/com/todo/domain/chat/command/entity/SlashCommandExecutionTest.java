package com.todo.domain.chat.command.entity;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandExecutionTest {

    @Test
    void 생성_직후에는_PENDING이고_결과가_없다() {
        SlashCommandExecution execution = createPending();

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.PENDING);
        assertThat(execution.getResultJson()).isNull();
        assertThat(execution.getExecutedAt()).isNull();
    }

    @Test
    void complete하면_DONE으로_전이하고_결과와_실행시각을_채운다() {
        SlashCommandExecution execution = createPending();
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 4, 12, 0);

        execution.complete("{\"count\":1}", executedAt);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.DONE);
        assertThat(execution.getResultJson()).isEqualTo("{\"count\":1}");
        assertThat(execution.getExecutedAt()).isEqualTo(executedAt);
    }

    @Test
    void fail하면_FAILED로_전이하고_결과는_비어있다() {
        SlashCommandExecution execution = createPending();
        LocalDateTime failedAt = LocalDateTime.of(2026, 8, 23, 12, 0);

        execution.fail(failedAt);

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.FAILED);
        assertThat(execution.getResultJson()).isNull();
        assertThat(execution.getExecutedAt()).isEqualTo(failedAt);
    }

    @Test
    void 이미_DONE이면_fail해도_결과를_덮어쓰지_않는다() {
        SlashCommandExecution execution = createPending();
        LocalDateTime executedAt = LocalDateTime.of(2026, 8, 23, 12, 0);
        execution.complete("{\"count\":1}", executedAt);

        execution.fail(executedAt.plusMinutes(1));

        assertThat(execution.getStatus()).isEqualTo(SlashCommandExecutionStatus.DONE);
        assertThat(execution.getResultJson()).isEqualTo("{\"count\":1}");
        assertThat(execution.getExecutedAt()).isEqualTo(executedAt);
    }

    private SlashCommandExecution createPending() {
        Team team = Team.create("팀", null, "ABCDEFGH");
        User executor = User.create("user1", "encoded", "닉네임1", null);
        TeamChatMessage message = TeamChatMessage.create(team, executor, "/내할일");
        return SlashCommandExecution.createPending(team, executor, message, SlashCommand.MY_TODOS);
    }
}
