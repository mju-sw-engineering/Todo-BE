package com.todo.global.file.service;

import com.todo.global.file.entity.FileDeletionOutbox;
import com.todo.global.file.event.FileDeletionEnqueuedEvent;
import com.todo.global.file.repository.FileDeletionOutboxRepository;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDeletionOutboxService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final FileDeletionOutboxRepository fileDeletionOutboxRepository;
    private final FileService fileService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 호출자 트랜잭션 안에서 삭제할 키를 적재한다. 트랜잭션이 롤백되면 outbox도 함께 롤백된다.
     */
    @Transactional
    public void enqueueAll(Collection<String> objectKeys) {
        if (objectKeys == null) {
            return;
        }

        objectKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .forEach(this::enqueue);
    }

    /**
     * 단건 삭제를 시도한다. 비관적 락으로 커밋 직후 처리와 폴러의 중복 실행을 막는다.
     */
    @Transactional
    public void dispatch(Long outboxId) {
        FileDeletionOutbox outbox = fileDeletionOutboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !outbox.isPending()) {
            return;
        }

        try {
            fileService.deleteObjectOrThrow(outbox.getObjectKey());
            outbox.markDeleted();
        } catch (RuntimeException e) {
            outbox.recordFailure(LocalDateTime.now(KST));
            log.warn(
                    "FILE_DELETE_FAILED outboxId={}, attemptCount={}, status={}, exceptionType={}",
                    outbox.getId(),
                    outbox.getAttemptCount(),
                    outbox.getStatus(),
                    e.getClass().getSimpleName()
            );
        }
    }

    private void enqueue(String objectKey) {
        FileDeletionOutbox outbox = fileDeletionOutboxRepository.save(FileDeletionOutbox.create(
                objectKey,
                LocalDateTime.now(KST)
        ));
        eventPublisher.publishEvent(new FileDeletionEnqueuedEvent(outbox.getId()));
    }
}
