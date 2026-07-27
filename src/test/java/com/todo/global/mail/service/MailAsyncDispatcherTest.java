package com.todo.global.mail.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class MailAsyncDispatcherTest {

    @InjectMocks
    private MailAsyncDispatcher mailAsyncDispatcher;

    @Mock
    private MailOutboxService mailOutboxService;

    @Test
    void outbox_발송을_위임한다() {
        mailAsyncDispatcher.dispatch(5L);

        then(mailOutboxService).should().dispatch(5L);
    }

    @Test
    void outbox_발송이_실패해도_예외를_전파하지_않는다() {
        willThrow(new RuntimeException("boom")).given(mailOutboxService).dispatch(5L);

        assertThatCode(() -> mailAsyncDispatcher.dispatch(5L))
                .doesNotThrowAnyException();
    }
}
