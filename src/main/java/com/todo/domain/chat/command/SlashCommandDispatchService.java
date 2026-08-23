package com.todo.domain.chat.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 슬래시 명령어 매칭과 실행을 담당한다. 매칭·PENDING 저장은
 * {@link com.todo.domain.chat.service.TeamChatService#saveMessage}와 같은 트랜잭션 안에서
 * 동기로 일어나고, 실제 핸들러 실행은 그 트랜잭션이 커밋된 뒤 이벤트로 넘어간다 — 명령어
 * 처리(특히 AI 호출 등 느릴 수 있는 작업)가 채팅 메시지 브로드캐스트 자체를 막지 않게 하기 위함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlashCommandDispatchService {

    private final SlashCommandRegistry registry;
    private final SlashCommandExecutionRepository executionRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public Optional<SlashCommand> match(String content) {
        return SlashCommand.fromText(content);
    }

    /**
     * 호출자(TeamChatService.saveMessage)가 이미 활성 트랜잭션 안에 있다고 가정한다 — 트랜잭션
     * 없이 호출하면 커밋 이벤트가 발화하지 않아 아래 {@link #completeExecution}이 영영 안 불린다.
     */
    @Transactional
    public void dispatchIfHandled(Team team, User executor, TeamChatMessage chatMessage, SlashCommand command) {
        if (registry.findHandler(command).isEmpty()) {
            return;
        }

        SlashCommandExecution execution = executionRepository.save(
                SlashCommandExecution.createPending(team, executor, chatMessage, command)
        );

        eventPublisher.publishEvent(new SlashCommandDispatchEvent(
                execution.getId(), team.getId(), executor.getId(), chatMessage.getId(), command
        ));
    }

    /**
     * 커밋 후 리스너가 새 트랜잭션에서 호출한다. 여기서 실패해도 원래 요청은 이미 응답이 나간
     * 뒤라 사용자에게 전달할 방법이 없다 — 로깅만 하고 실행 결과는 PENDING에 남겨둔다.
     */
    @Transactional
    public void completeExecution(SlashCommandDispatchEvent event) {
        try {
            SlashCommandHandler handler = registry.findHandler(event.command()).orElse(null);
            if (handler == null) {
                return;
            }
            SlashCommandExecution execution = executionRepository.findById(event.executionId()).orElse(null);
            if (execution == null) {
                return;
            }

            Team team = teamRepository.getReferenceById(event.teamId());
            User executor = userRepository.getReferenceById(event.executorId());

            Object result = handler.execute(team, executor);
            execution.complete(objectMapper.writeValueAsString(result), LocalDateTime.now());

            if (event.command().scope() == SlashCommandScope.PERSONAL) {
                notificationService.pushAll(
                        List.of(executor),
                        notificationMessageFactory.slashCommandResult(event.command().commandText()),
                        event.chatMessageId(),
                        event.teamId()
                );
            }
        } catch (Exception e) {
            log.warn("슬래시 명령어 실행 실패. command={}, executionId={}", event.command(), event.executionId(), e);
        }
    }
}
