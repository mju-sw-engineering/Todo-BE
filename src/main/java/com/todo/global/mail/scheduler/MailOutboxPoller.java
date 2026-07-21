package com.todo.global.mail.scheduler;

import com.todo.global.mail.entity.MailOutboxStatus;
import com.todo.global.mail.repository.MailOutboxRepository;
import com.todo.global.mail.service.MailOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * PENDING 상태의 메일을 주기적으로 재시도하고, 오래된 처리 완료 건을 정리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailOutboxPoller {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 50;
    private static final int RETENTION_DAYS = 7;

    private final MailOutboxRepository mailOutboxRepository;
    private final MailOutboxService mailOutboxService;

    @Scheduled(fixedDelayString = "${mail.outbox.poll-interval-ms:10000}")
    public void retryPending() {
        List<Long> ids = mailOutboxRepository.findDispatchableIds(LocalDateTime.now(KST), PageRequest.of(0, BATCH_SIZE));
        for (Long id : ids) {
            try {
                mailOutboxService.dispatch(id);
            } catch (RuntimeException e) {
                log.warn("메일 재시도 발송 실패. outboxId={}", id, e);
            }
        }
    }

    @Scheduled(cron = "${mail.outbox.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void cleanupProcessed() {
        LocalDateTime threshold = LocalDateTime.now(KST).minusDays(RETENTION_DAYS);
        int deleted = mailOutboxRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(MailOutboxStatus.SENT, MailOutboxStatus.FAILED), threshold
        );
        if (deleted > 0) {
            log.info("오래된 메일 outbox {}건 정리", deleted);
        }
    }
}
