package com.todo.domain.chat.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SlashCommandDispatchEventListenerTest {

    @InjectMocks
    private SlashCommandDispatchEventListener listener;

    @Mock
    private SlashCommandAsyncDispatcher asyncDispatcher;

    @Mock
    private SlashCommandDispatchService dispatchService;

    private final SlashCommandDispatchEvent event =
            new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.TEAM_STATUS);

    @Test
    void 이벤트를_비동기_디스패처에_넘긴다() {
        listener.onSlashCommandDispatch(event);

        then(asyncDispatcher).should().dispatch(event);
        then(dispatchService).should(never()).markFailed(event);
    }

    @Test
    void 큐가_포화돼_제출이_거부되면_FAILED로_확정한다() {
        willThrow(new TaskRejectedException("큐 포화")).given(asyncDispatcher).dispatch(event);

        listener.onSlashCommandDispatch(event);

        then(dispatchService).should().markFailed(event);
    }
}
