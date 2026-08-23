package com.todo.domain.chat.command.entity;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "slash_command_executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlashCommandExecution extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /**
     * 실행한 사용자. 탈퇴하면 null이 된다 — {@link TeamChatMessage#getSender()}와 같은 정책으로,
     * FK는 RESTRICT를 유지하고 애플리케이션이 명시적으로 정리한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executor_id")
    private User executor;

    /**
     * 이 실행을 촉발한 채팅 메시지. DB 레벨 {@code ON DELETE CASCADE}가 걸려있어, 채팅 정리
     * 스케줄러가 이 메시지를 지우면 실행 결과도 같이 사라진다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private TeamChatMessage chatMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SlashCommand command;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlashCommandExecutionStatus status;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    private LocalDateTime executedAt;

    public static SlashCommandExecution createPending(
            Team team, User executor, TeamChatMessage chatMessage, SlashCommand command
    ) {
        SlashCommandExecution execution = new SlashCommandExecution();
        execution.team = team;
        execution.executor = executor;
        execution.chatMessage = chatMessage;
        execution.command = command;
        execution.status = SlashCommandExecutionStatus.PENDING;
        return execution;
    }

    public void complete(String resultJson, LocalDateTime executedAt) {
        this.resultJson = resultJson;
        this.executedAt = executedAt;
        this.status = SlashCommandExecutionStatus.DONE;
    }
}
