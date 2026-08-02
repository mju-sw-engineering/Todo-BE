package com.todo.global.mail.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class MailAsyncConfig {

    @Bean(name = "mailTaskExecutor")
    public ThreadPoolTaskExecutor mailTaskExecutor(
            @Value("${mail.async.core-pool-size:2}") int corePoolSize,
            @Value("${mail.async.max-pool-size:4}") int maxPoolSize,
            @Value("${mail.async.queue-capacity:100}") int queueCapacity,
            @Value("${mail.async.await-termination-seconds:15}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("mail-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        log.info(
                "메일 비동기 스레드 풀 설정. corePoolSize={}, maxPoolSize={}, queueCapacity={}, awaitTerminationSeconds={}",
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                awaitTerminationSeconds
        );
        return executor;
    }
}
