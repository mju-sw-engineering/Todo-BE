package com.todo.global.file.scheduler;

import com.todo.global.file.entity.FileDeletionOutboxStatus;
import com.todo.global.file.repository.FileDeletionOutboxRepository;
import com.todo.global.file.service.FileDeletionOutboxService;
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
public class FileDeletionOutboxPoller {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 50;
    private static final int RETENTION_DAYS = 7;

    private final FileDeletionOutboxRepository fileDeletionOutboxRepository;
    private final FileDeletionOutboxService fileDeletionOutboxService;

    @Scheduled(fixedDelayString = "${file-deletion.outbox.poll-interval-ms:10000}")
    public void retryPending() {
        List<Long> ids = fileDeletionOutboxRepository.findDispatchableIds(
                LocalDateTime.now(KST),
                PageRequest.of(0, BATCH_SIZE)
        );
        for (Long id : ids) {
            try {
                fileDeletionOutboxService.dispatch(id);
            } catch (RuntimeException e) {
                log.warn("파일 삭제 재시도 중 예외 발생. outboxId={}", id, e);
            }
        }
    }

    @Scheduled(cron = "${file-deletion.outbox.cleanup-cron:0 30 4 * * *}")
    @Transactional
    public void cleanupProcessed() {
        LocalDateTime threshold = LocalDateTime.now(KST).minusDays(RETENTION_DAYS);
        int deleted = fileDeletionOutboxRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(FileDeletionOutboxStatus.DELETED),
                threshold
        );
        if (deleted > 0) {
            log.info("오래된 파일 삭제 outbox 정리 완료. deletedCount={}", deleted);
        }
    }
}
