package com.todo.global.file.event;

import com.todo.global.file.service.FileDeletionAsyncDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileDeletionOutboxEventListener {

    private final FileDeletionAsyncDispatcher fileDeletionAsyncDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileDeletionEnqueued(FileDeletionEnqueuedEvent event) {
        try {
            fileDeletionAsyncDispatcher.dispatch(event.outboxId());
        } catch (RuntimeException e) {
            log.warn(
                    "FILE_DELETE_ASYNC_SUBMIT_FAILED outboxId={}, exceptionType={}, 폴러 재시도 예정",
                    event.outboxId(),
                    e.getClass().getSimpleName()
            );
        }
    }
}
