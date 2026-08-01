package com.todo.global.file.entity;

import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_deletion_outbox",
        indexes = @Index(
                name = "idx_file_deletion_outbox_status_next_attempt",
                columnList = "status, next_attempt_at"
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileDeletionOutbox extends BaseTimeEntity {

    private static final long BASE_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 600;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1024)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private FileDeletionOutboxStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    public static FileDeletionOutbox create(
            String objectKey,
            LocalDateTime now
    ) {
        FileDeletionOutbox outbox = new FileDeletionOutbox();
        outbox.objectKey = objectKey;
        outbox.status = FileDeletionOutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = now;
        return outbox;
    }

    public boolean isPending() {
        return status == FileDeletionOutboxStatus.PENDING;
    }

    public void markDeleted() {
        this.status = FileDeletionOutboxStatus.DELETED;
    }

    public void recordFailure(LocalDateTime now) {
        this.attemptCount++;
        this.nextAttemptAt = now.plusSeconds(backoffSeconds());
    }

    private long backoffSeconds() {
        long factor = 1L << Math.min(attemptCount - 1, 20);
        return Math.min(BASE_BACKOFF_SECONDS * factor, MAX_BACKOFF_SECONDS);
    }
}
