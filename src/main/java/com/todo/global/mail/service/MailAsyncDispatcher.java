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
                    "메일 비동기 발송 실패. outboxId={}",
                    outboxId,
                    e
            );
        }
    }
}
