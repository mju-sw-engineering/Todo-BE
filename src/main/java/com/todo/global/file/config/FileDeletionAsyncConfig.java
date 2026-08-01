package com.todo.global.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class FileDeletionAsyncConfig {

    @Bean(name = "fileDeletionTaskExecutor")
    public ThreadPoolTaskExecutor fileDeletionTaskExecutor(
            @Value("${file-deletion.async.core-pool-size:1}") int corePoolSize,
            @Value("${file-deletion.async.max-pool-size:2}") int maxPoolSize,
            @Value("${file-deletion.async.queue-capacity:100}") int queueCapacity,
            @Value("${file-deletion.async.await-termination-seconds:15}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("file-delete-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        return executor;
    }
}
