package com.todo.global.file.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class FileDeletionAsyncConfigTest {

    @Test
    void 파일삭제_전용_executor를_설정한다() {
        FileDeletionAsyncConfig config = new FileDeletionAsyncConfig();
        ThreadPoolTaskExecutor executor = config.fileDeletionTaskExecutor(1, 2, 3, 4);
        executor.initialize();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("file-delete-");
        } finally {
            executor.shutdown();
        }
    }
}
