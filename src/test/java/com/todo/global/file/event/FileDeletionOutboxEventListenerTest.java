package com.todo.global.file.event;

import com.todo.global.file.service.FileDeletionAsyncDispatcher;
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
class FileDeletionOutboxEventListenerTest {

    @InjectMocks
    private FileDeletionOutboxEventListener listener;

    @Mock
    private FileDeletionAsyncDispatcher dispatcher;

    @Test
    void 커밋후_이벤트를_받으면_비동기_삭제를_요청한다() {
        listener.onFileDeletionEnqueued(new FileDeletionEnqueuedEvent(5L));

        then(dispatcher).should().dispatch(5L);
    }

    @Test
    void 비동기_작업_제출이_거부돼도_폴러_재시도를_위해_예외를_전파하지_않는다() {
        willThrow(new TaskRejectedException("queue full")).given(dispatcher).dispatch(5L);

        assertThatCode(() -> listener.onFileDeletionEnqueued(new FileDeletionEnqueuedEvent(5L)))
                .doesNotThrowAnyException();
    }
}
