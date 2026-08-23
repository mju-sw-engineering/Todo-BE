package com.todo.domain.chat.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SlashCommandAsyncDispatcherTest {

    @InjectMocks
    private SlashCommandAsyncDispatcher asyncDispatcher;

    @Mock
    private SlashCommandDispatchService dispatchService;

    private final SlashCommandDispatchEvent event =
            new SlashCommandDispatchEvent(5000L, 100L, 1L, 1000L, SlashCommand.TEAM_STATUS);

    @Test
    void 정상_완료되면_실패_처리를_하지_않는다() {
        asyncDispatcher.dispatch(event);

        then(dispatchService).should().executeAndComplete(event);
        then(dispatchService).should(never()).markFailed(event);
    }

    @Test
    void 실행_중_예외가_나면_FAILED로_확정한다() {
        willThrow(new IllegalStateException("핸들러 실패")).given(dispatchService).executeAndComplete(event);

        asyncDispatcher.dispatch(event);

        then(dispatchService).should().markFailed(event);
    }
}
