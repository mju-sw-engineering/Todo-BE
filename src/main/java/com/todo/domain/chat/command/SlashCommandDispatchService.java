package com.todo.domain.chat.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 슬래시 명령어 매칭과 실행을 담당한다. 매칭·PENDING 저장은
 * {@link com.todo.domain.chat.service.TeamChatService#saveMessage}와 같은 트랜잭션 안에서
 * 동기로 일어나고, 실제 핸들러 실행은 그 트랜잭션이 커밋된 뒤 {@link SlashCommandAsyncDispatcher}가
 * 별도 스레드에서 한다 — 명령어 처리(특히 AI 호출 등 느릴 수 있는 작업)가 채팅 메시지
 * 브로드캐스트를 막거나 WebSocket 스레드를 점유하지 않게 하기 위함.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlashCommandDispatchService {

    private final SlashCommandRegistry registry;
    private final SlashCommandExecutionRepository executionRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
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
     * 없이 호출하면 커밋 이벤트가 발화하지 않아 핸들러가 영영 실행되지 않는다.
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
     * 비동기 스레드에서 호출된다 ({@link SlashCommandAsyncDispatcher}). 새 스레드에는 바인딩된
     * 트랜잭션이 없으므로 REQUIRED로 새 트랜잭션이 열린다.
     *
     * <p>핸들러 실행과 결과 저장을 한 트랜잭션에 묶는다. 핸들러가 예외를 던지면 그대로 전파해
     * 트랜잭션을 롤백시키고, 실패 확정은 호출자가 {@link #markFailed}로 별도 트랜잭션에서 한다 —
     * 같은 트랜잭션 안에서 FAILED를 쓰면 핸들러 안쪽 리포지토리 호출이 남긴 rollback-only 표시
     * 때문에 커밋이 거부될 수 있다.
     *
     * @throws RuntimeException 핸들러 실패·직렬화 실패. 호출자가 FAILED 처리한다
     */
    @Transactional
    public void executeAndComplete(SlashCommandDispatchEvent event) {
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
        execution.complete(toJson(result), LocalDateTime.now());

        pushResultReady(event, executor);
    }

    /**
     * 실패를 확정한다. 두 곳에서 불린다 — 비동기 스레드(트랜잭션 없음)와 AFTER_COMMIT 리스너
     * (원래 트랜잭션 리소스가 아직 바인딩된 상태). 후자에서 REQUIRED는 이미 커밋된 트랜잭션에
     * 참여해 두 번째 커밋이 없으므로, 양쪽 모두에서 안전한 REQUIRES_NEW를 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(SlashCommandDispatchEvent event) {
        SlashCommandExecution execution = executionRepository.findById(event.executionId()).orElse(null);
        if (execution == null) {
            return;
        }
        execution.fail(LocalDateTime.now());
        User executor = userRepository.getReferenceById(event.executorId());
        pushResultReady(event, executor);
    }

    /**
     * 결과가 확정됐음(성공·실패 모두)을 push로 알린다. 데이터는 싣지 않고 FE가 결과 조회 API로
     * 가져간다. PERSONAL은 실행자에게만, TEAM은 팀원 전원에게 — 팀용 결과를 실행자만 알면
     * 나머지 팀원은 칩을 두드려 폴링해야 한다.
     */
    private void pushResultReady(SlashCommandDispatchEvent event, User executor) {
        List<User> receivers = event.command().scope() == SlashCommandScope.PERSONAL
                ? List.of(executor)
                : teamMemberRepository.findByTeamIdWithUser(event.teamId()).stream()
                        .map(TeamMember::getUser)
                        .toList();
        notificationService.pushAll(
                receivers,
                notificationMessageFactory.slashCommandResult(event.command().commandText()),
                event.chatMessageId(),
                event.teamId()
        );
    }

    private String toJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new BusinessException("명령어 실행 결과를 직렬화하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
