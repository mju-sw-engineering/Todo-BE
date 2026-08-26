package com.todo.global.file.scheduler;

import com.todo.global.file.config.OrphanCleanupProperties;
import com.todo.global.file.service.OrphanFileCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrphanFileCleanupSchedulerTest {

    @Mock
    private OrphanFileCleanupService orphanFileCleanupService;

    private OrphanFileCleanupScheduler scheduler(boolean enabled) {
        return new OrphanFileCleanupScheduler(
                orphanFileCleanupService,
                new OrphanCleanupProperties(enabled, true, 24, 500)
        );
    }

    @Test
    void 활성화_상태에서는_정리를_실행한다() {
        scheduler(true).cleanup();

        then(orphanFileCleanupService).should().cleanupExpired(any(LocalDateTime.class));
    }

    @Test
    void 비활성화_상태에서는_정리를_실행하지_않는다() {
        scheduler(false).cleanup();

        then(orphanFileCleanupService).should(never()).cleanupExpired(any());
    }

    @Test
    void 정리_실행이_실패해도_예외를_밖으로_던지지_않는다() {
        doThrow(new RuntimeException("boom"))
                .when(orphanFileCleanupService).cleanupExpired(any(LocalDateTime.class));

        assertThatCode(() -> scheduler(true).cleanup()).doesNotThrowAnyException();
    }
}
