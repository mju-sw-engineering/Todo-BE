package com.todo.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 슬래시 명령어 핸들러 실행용 스레드 풀. {@code MailAsyncConfig}·{@code FileDeletionAsyncConfig}와 같은 구조다.
 *
 * <p>풀 크기를 작게 두는 이유: 핸들러는 트랜잭션 안에서 실행되므로 느린 핸들러(AI 호출, 최대 30초)가
 * 도는 동안 DB 커넥션 하나를 점유한다. 동시 실행 수가 곧 점유 커넥션 상한이다.
 * 큐가 차면 작업이 거부되고 실행은 FAILED로 확정된다 — 사용자는 명령어를 다시 치면 된다.
 */
@Slf4j
@Configuration
public class SlashCommandAsyncConfig {

    @Bean(name = "slashCommandTaskExecutor")
    public ThreadPoolTaskExecutor slashCommandTaskExecutor(
            @Value("${slash-command.async.core-pool-size:2}") int corePoolSize,
            @Value("${slash-command.async.max-pool-size:2}") int maxPoolSize,
            @Value("${slash-command.async.queue-capacity:20}") int queueCapacity,
            @Value("${slash-command.async.await-termination-seconds:35}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("slash-command-");
        // 종료 시 실행 중인 핸들러를 기다린다. 끊기면 PENDING 고아가 남는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        log.info(
                "슬래시 명령어 비동기 스레드 풀 설정. corePoolSize={}, maxPoolSize={}, queueCapacity={}, awaitTerminationSeconds={}",
                corePoolSize,
                maxPoolSize,
                queueCapacity,
                awaitTerminationSeconds
        );
        return executor;
    }
}
