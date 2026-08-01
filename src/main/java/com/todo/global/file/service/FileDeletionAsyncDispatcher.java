package com.todo.global.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDeletionAsyncDispatcher {

    private final FileDeletionOutboxService fileDeletionOutboxService;

    @Async("fileDeletionTaskExecutor")
    public void dispatch(Long outboxId) {
        try {
            fileDeletionOutboxService.dispatch(outboxId);
        } catch (RuntimeException e) {
            log.warn(
                    "FILE_DELETE_ASYNC_DISPATCH_FAILED outboxId={}, exceptionType={}",
                    outboxId,
                    e.getClass().getSimpleName()
            );
        }
    }
}
