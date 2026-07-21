package com.todo.global.mail.event;

import com.todo.global.mail.service.MailOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 적재 트랜잭션 커밋 후 즉시 발송을 시도한다. 실패해도 폴러가 재시도하므로 예외는 로깅만 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailOutboxEventListener {

    private final MailOutboxService mailOutboxService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMailEnqueued(MailEnqueuedEvent event) {
        try {
            mailOutboxService.dispatch(event.outboxId());
        } catch (RuntimeException e) {
            log.warn("메일 즉시 발송 실패, 폴러 재시도 예정. outboxId={}", event.outboxId(), e);
        }
    }
}
