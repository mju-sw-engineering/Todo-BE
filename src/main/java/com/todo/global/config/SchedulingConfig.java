package com.todo.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 설정.
 * 테스트 프로파일에서는 {@code todo.scheduling.enabled=false}로 비활성화한다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "todo.scheduling", name = "enabled", matchIfMissing = true)
public class SchedulingConfig {
}
