package com.todo.global.mail.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailAsyncDispatcher {

    private final MailOutboxService mailOutboxService;

    @Async("mailTaskExecutor")
    public void dispatch(Long outboxId) {
        try {
            mailOutboxService.dispatch(outboxId);
        } catch (RuntimeException e) {
            log.warn(
                    "MAIL_ASYNC_DISPATCH_FAILED outboxId={}, exceptionType={}",
                    outboxId,
                    e.getClass().getSimpleName()
            );
        }
    }
}
