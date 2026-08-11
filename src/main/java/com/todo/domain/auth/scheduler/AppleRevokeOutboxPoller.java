package com.todo.domain.auth.scheduler;

import com.todo.domain.auth.entity.AppleRevokeOutboxStatus;
import com.todo.domain.auth.repository.AppleRevokeOutboxRepository;
import com.todo.domain.auth.service.AppleRevokeOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppleRevokeOutboxPoller {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 50;
    private static final int RETENTION_DAYS = 30;

    private final AppleRevokeOutboxRepository appleRevokeOutboxRepository;
    private final AppleRevokeOutboxService appleRevokeOutboxService;

    @Scheduled(fixedDelayString = "${apple-revoke.outbox.poll-interval-ms:60000}")
    public void retryPending() {
        List<Long> ids = appleRevokeOutboxRepository.findDispatchableIds(
                LocalDateTime.now(KST),
                PageRequest.of(0, BATCH_SIZE)
        );
        for (Long id : ids) {
            try {
                appleRevokeOutboxService.dispatch(id);
            } catch (RuntimeException e) {
                log.warn("Apple revoke 재시도 중 예외 발생. outboxId={}", id, e);
            }
        }
    }

    @Scheduled(cron = "${apple-revoke.outbox.cleanup-cron:0 40 4 * * *}")
    @Transactional
    public void cleanupProcessed() {
        LocalDateTime threshold = LocalDateTime.now(KST).minusDays(RETENTION_DAYS);
        int deleted = appleRevokeOutboxRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(AppleRevokeOutboxStatus.REVOKED, AppleRevokeOutboxStatus.FAILED),
                threshold
        );
        if (deleted > 0) {
            log.info("오래된 Apple revoke outbox 정리 완료. deletedCount={}", deleted);
        }
    }
}
