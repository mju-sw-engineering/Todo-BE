package com.todo.domain.auth.entity;

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

/**
 * 탈퇴 직후 시도하는 Apple revoke가 실패했을 때만 적재되는 재시도 큐.
 *
 * <p>file_deletion_outbox와 달리 무한 재시도하지 않는다. 재시도로 해소되는 건 일시적
 * 장애뿐이고, refresh token 자체가 죽은 경우는 사람이 봐도 고칠 수 없어 계속 재시도해봐야
 * 자원만 쓴다. {@link #MAX_ATTEMPTS}를 넘기면 {@link AppleRevokeOutboxStatus#FAILED}로
 * 확정해 더 이상 재시도 대상에서 빠지게 한다.
 */
@Entity
@Table(name = "apple_revoke_outbox",
        indexes = @Index(
                name = "idx_apple_revoke_outbox_status_next_attempt",
                columnList = "status, next_attempt_at"
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppleRevokeOutbox extends BaseTimeEntity {

    private static final long BASE_BACKOFF_SECONDS = 60;
    private static final long MAX_BACKOFF_SECONDS = 3600;
    static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1024)
    private String appleRefreshToken;

    @Column(nullable = false)
    private String appleClientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AppleRevokeOutboxStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    public static AppleRevokeOutbox create(
            Long userId,
            String appleRefreshToken,
            String appleClientId,
            LocalDateTime now
    ) {
        AppleRevokeOutbox outbox = new AppleRevokeOutbox();
        outbox.userId = userId;
        outbox.appleRefreshToken = appleRefreshToken;
        outbox.appleClientId = appleClientId;
        outbox.status = AppleRevokeOutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = now;
        return outbox;
    }

    public boolean isPending() {
        return status == AppleRevokeOutboxStatus.PENDING;
    }

    public void markRevoked() {
        this.status = AppleRevokeOutboxStatus.REVOKED;
    }

    public void recordFailure(LocalDateTime now) {
        this.attemptCount++;
        if (attemptCount >= MAX_ATTEMPTS) {
            this.status = AppleRevokeOutboxStatus.FAILED;
            return;
        }
        this.nextAttemptAt = now.plusSeconds(backoffSeconds());
    }

    private long backoffSeconds() {
        long factor = 1L << Math.min(attemptCount - 1, 20);
        return Math.min(BASE_BACKOFF_SECONDS * factor, MAX_BACKOFF_SECONDS);
    }
}
