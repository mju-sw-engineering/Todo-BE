package com.todo.global.mail.service;

import com.todo.global.mail.config.MailAsyncConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

@SpringJUnitConfig(classes = {
        MailAsyncConfig.class,
        MailAsyncExecutionTest.TestConfig.class
})
@TestPropertySource(properties = {
        "mail.async.core-pool-size=1",
        "mail.async.max-pool-size=1",
        "mail.async.queue-capacity=1",
        "mail.async.await-termination-seconds=1"
})
class MailAsyncExecutionTest {

    @Autowired
    private MailAsyncDispatcher mailAsyncDispatcher;

    @Autowired
    private MailOutboxService mailOutboxService;

    private final CountDownLatch releaseDispatch = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        releaseDispatch.countDown();
        reset(mailOutboxService);
    }

    @Test
    void 호출자는_SMTP_발송_완료를_기다리지_않는다() throws Exception {
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        AtomicReference<String> dispatchThreadName = new AtomicReference<>();
        willAnswer(invocation -> {
            dispatchThreadName.set(Thread.currentThread().getName());
            dispatchStarted.countDown();
            releaseDispatch.await(2, TimeUnit.SECONDS);
            return null;
        }).given(mailOutboxService).dispatch(5L);

        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<?> submission = caller.submit(() -> mailAsyncDispatcher.dispatch(5L));

            submission.get(500, TimeUnit.MILLISECONDS);
            assertThat(dispatchStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatchThreadName.get()).startsWith("mail-dispatch-");
        } finally {
            releaseDispatch.countDown();
            caller.shutdownNow();
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        MailOutboxService mailOutboxService() {
            return mock(MailOutboxService.class);
        }

        @Bean
        MailAsyncDispatcher mailAsyncDispatcher(MailOutboxService mailOutboxService) {
            return new MailAsyncDispatcher(mailOutboxService);
        }
    }
}
