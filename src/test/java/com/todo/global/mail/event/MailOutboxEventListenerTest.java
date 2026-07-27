package com.todo.global.mail.event;

import com.todo.global.mail.service.MailAsyncDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class MailOutboxEventListenerTest {

    @InjectMocks
    private MailOutboxEventListener mailOutboxEventListener;

    @Mock
    private MailAsyncDispatcher mailAsyncDispatcher;

    @Test
    void 이벤트를_받으면_비동기_발송을_요청한다() {
        mailOutboxEventListener.onMailEnqueued(new MailEnqueuedEvent(5L));

        then(mailAsyncDispatcher).should().dispatch(5L);
    }

    @Test
    void 비동기_작업_제출이_거부되어도_예외를_전파하지_않는다() {
        willThrow(new TaskRejectedException("queue full")).given(mailAsyncDispatcher).dispatch(5L);

        assertThatCode(() -> mailOutboxEventListener.onMailEnqueued(new MailEnqueuedEvent(5L)))
                .doesNotThrowAnyException();
    }

    @Test
    void 비동기_작업_제출중_예상하지_못한_오류도_요청으로_전파하지_않는다() {
        willThrow(new IllegalStateException("executor unavailable")).given(mailAsyncDispatcher).dispatch(5L);

        assertThatCode(() -> mailOutboxEventListener.onMailEnqueued(new MailEnqueuedEvent(5L)))
                .doesNotThrowAnyException();
    }
}
