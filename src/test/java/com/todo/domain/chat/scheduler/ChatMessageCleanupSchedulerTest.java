package com.todo.domain.chat.scheduler;

import com.todo.domain.chat.repository.TeamChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageCleanupSchedulerTest {

    @InjectMocks
    private ChatMessageCleanupScheduler scheduler;

    @Mock
    private TeamChatMessageRepository teamChatMessageRepository;

    @Test
    void 일주일_초과_메시지를_삭제한다() {
        LocalDateTime before = LocalDateTime.now().minusDays(7);

        scheduler.cleanupOldMessages();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(teamChatMessageRepository).deleteByCreatedAtBefore(captor.capture());

        LocalDateTime cutoff = captor.getValue();
        LocalDateTime after = LocalDateTime.now().minusDays(7);
        assertThat(cutoff).isBetween(before, after);
    }

    @Test
    void 삭제_대상이_없어도_예외가_발생하지_않는다() {
        scheduler.cleanupOldMessages();

        verify(teamChatMessageRepository).deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any());
    }
}
