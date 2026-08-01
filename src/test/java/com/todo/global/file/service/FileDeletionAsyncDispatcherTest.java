package com.todo.global.file.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class FileDeletionAsyncDispatcherTest {

    @InjectMocks
    private FileDeletionAsyncDispatcher dispatcher;

    @Mock
    private FileDeletionOutboxService fileDeletionOutboxService;

    @Test
    void 비동기_삭제를_outbox_서비스에_위임한다() {
        dispatcher.dispatch(5L);

        then(fileDeletionOutboxService).should().dispatch(5L);
    }

    @Test
    void 비동기_삭제중_오류가_발생해도_호출자에게_전파하지_않는다() {
        willThrow(new IllegalStateException("DB 오류")).given(fileDeletionOutboxService).dispatch(5L);

        assertThatCode(() -> dispatcher.dispatch(5L)).doesNotThrowAnyException();
    }
}
