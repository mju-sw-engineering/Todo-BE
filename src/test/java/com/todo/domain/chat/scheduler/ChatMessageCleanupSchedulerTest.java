package com.todo.domain.chat.scheduler;

import com.todo.domain.chat.service.ChatMessageCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ChatMessageCleanupSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 1000;

    @InjectMocks
    private ChatMessageCleanupScheduler scheduler;

    @Mock
    private ChatMessageCleanupService chatMessageCleanupService;

    @BeforeEach
    void setRetentionDays() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
    }

    @Test
    void 보관기간이_지난_메시지를_배치_단위로_삭제한다() {
        LocalDateTime before = LocalDateTime.now(KST).minusDays(7);
        given(chatMessageCleanupService.deleteBatch(any(), anyInt())).willReturn(10);

        scheduler.cleanupOldMessages();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(chatMessageCleanupService).should().deleteBatch(captor.capture(), eq(BATCH_SIZE));
        LocalDateTime after = LocalDateTime.now(KST).minusDays(7);
        assertThat(captor.getValue()).isBetween(before, after);
    }

    @Test
    void 배치가_가득_차면_다음_배치를_이어서_삭제한다() {
        given(chatMessageCleanupService.deleteBatch(any(), anyInt()))
                .willReturn(BATCH_SIZE, BATCH_SIZE, 7);

        scheduler.cleanupOldMessages();

        then(chatMessageCleanupService).should(times(3)).deleteBatch(any(), eq(BATCH_SIZE));
    }

    @Test
    void 삭제_대상이_없으면_한_번만_삭제를_시도하고_종료한다() {
        given(chatMessageCleanupService.deleteBatch(any(), anyInt())).willReturn(0);

        scheduler.cleanupOldMessages();

        then(chatMessageCleanupService).should(times(1)).deleteBatch(any(), eq(BATCH_SIZE));
    }

    @Test
    void 보관기간_설정값을_cutoff에_반영한다() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
        LocalDateTime before = LocalDateTime.now(KST).minusDays(30);
        given(chatMessageCleanupService.deleteBatch(any(), anyInt())).willReturn(0);

        scheduler.cleanupOldMessages();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(chatMessageCleanupService).should().deleteBatch(captor.capture(), eq(BATCH_SIZE));
        LocalDateTime after = LocalDateTime.now(KST).minusDays(30);
        assertThat(captor.getValue()).isBetween(before, after);
    }
}
