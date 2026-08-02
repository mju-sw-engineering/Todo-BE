package com.todo.global.mail.event;

import com.todo.global.mail.service.MailAsyncDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 적재 트랜잭션 커밋 후 비동기 발송을 요청한다. 제출에 실패해도 폴러가 재시도하므로 예외는 로깅만 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailOutboxEventListener {

    private final MailAsyncDispatcher mailAsyncDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMailEnqueued(MailEnqueuedEvent event) {
        try {
            mailAsyncDispatcher.dispatch(event.outboxId());
        } catch (TaskRejectedException e) {
            log.warn(
                    "메일 비동기 스레드 풀 작업 제출 거부. outboxId={}, 폴러 재시도 예정",
                    event.outboxId(),
                    e
            );
        } catch (RuntimeException e) {
            log.warn(
                    "메일 비동기 발송 요청 실패. outboxId={}, 폴러 재시도 예정",
                    event.outboxId(),
                    e
            );
        }
    }
}
