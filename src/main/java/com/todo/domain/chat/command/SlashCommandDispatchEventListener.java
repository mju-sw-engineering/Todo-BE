package com.todo.domain.chat.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 슬래시 명령어 실행 트랜잭션이 커밋된 뒤에만 핸들러를 비동기로 넘긴다.
 * 롤백된 채팅 메시지에 대한 명령어가 실행되는 것을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlashCommandDispatchEventListener {

    private final SlashCommandAsyncDispatcher asyncDispatcher;
    private final SlashCommandDispatchService dispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlashCommandDispatch(SlashCommandDispatchEvent event) {
        try {
            asyncDispatcher.dispatch(event);
        } catch (TaskRejectedException e) {
            // 큐 포화. 재시도 경로가 없으므로 바로 FAILED로 확정한다 — PENDING에 남겨두면
            // 사용자는 영원히 "처리 중"을 본다.
            log.warn("슬래시 명령어 비동기 작업 제출 거부. command={}, executionId={}",
                    event.command(), event.executionId(), e);
            dispatchService.markFailed(event);
        }
    }
}
