package com.todo.global.mail.event;

import com.todo.global.mail.service.MailOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class MailOutboxEventListenerTest {

    @InjectMocks
    private MailOutboxEventListener mailOutboxEventListener;

    @Mock
    private MailOutboxService mailOutboxService;

    @Test
    void 이벤트를_받으면_즉시_발송을_시도한다() {
        mailOutboxEventListener.onMailEnqueued(new MailEnqueuedEvent(5L));

        then(mailOutboxService).should().dispatch(5L);
    }

    @Test
    void 즉시_발송이_실패해도_예외를_전파하지_않는다() {
        willThrow(new RuntimeException("boom")).given(mailOutboxService).dispatch(5L);

        assertThatCode(() -> mailOutboxEventListener.onMailEnqueued(new MailEnqueuedEvent(5L)))
                .doesNotThrowAnyException();
    }
}
