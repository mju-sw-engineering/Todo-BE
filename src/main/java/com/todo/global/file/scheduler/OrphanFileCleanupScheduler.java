package com.todo.global.file.scheduler;

import com.todo.global.file.config.OrphanCleanupProperties;
import com.todo.global.file.service.OrphanFileCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 고아 파일 정리를 하루 한 번 트래픽 최저 시간대에 돌린다.
 * 기본 04:50 KST — outbox 정리(04:30)와 겹치지 않게 뒀다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileCleanupScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OrphanFileCleanupService orphanFileCleanupService;
    private final OrphanCleanupProperties properties;

    @Scheduled(cron = "${orphan-cleanup.cron:0 50 4 * * *}")
    public void cleanup() {
        if (!properties.enabled()) {
            return;
        }
        try {
            orphanFileCleanupService.cleanupExpired(LocalDateTime.now(KST));
        } catch (RuntimeException e) {
            // 실패해도 다음 날 실행이 남은 원장을 그대로 처리한다.
            log.error("고아 파일 정리 실행 실패", e);
        }
    }
}
