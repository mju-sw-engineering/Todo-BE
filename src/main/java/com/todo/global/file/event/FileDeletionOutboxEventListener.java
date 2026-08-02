package com.todo.global.file.event;

import com.todo.global.file.service.FileDeletionAsyncDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
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
        } catch (TaskRejectedException e) {
            log.warn(
                    "파일 삭제 비동기 스레드 풀 작업 제출 거부. outboxId={}, 폴러 재시도 예정",
                    event.outboxId(),
                    e
            );
        } catch (RuntimeException e) {
            log.warn(
                    "파일 삭제 비동기 요청 실패. outboxId={}, 폴러 재시도 예정",
                    event.outboxId(),
                    e
            );
        }
    }
}
