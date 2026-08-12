package com.todo.domain.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AppleRevokeOutboxTest {

    @Test
    void 생성하면_PENDING_상태로_즉시_재시도_대상이_된다() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 0);

        AppleRevokeOutbox outbox = AppleRevokeOutbox.create(1L, "apple-rt", "com.test.app", now);

        assertThat(outbox.getUserId()).isEqualTo(1L);
        assertThat(outbox.getAppleRefreshToken()).isEqualTo("apple-rt");
        assertThat(outbox.getAppleClientId()).isEqualTo("com.test.app");
        assertThat(outbox.getStatus()).isEqualTo(AppleRevokeOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now);
        assertThat(outbox.isPending()).isTrue();
    }

    @Test
    void revoke에_성공하면_REVOKED로_종결한다() {
        AppleRevokeOutbox outbox = AppleRevokeOutbox.create(1L, "apple-rt", "com.test.app", LocalDateTime.now());

        outbox.markRevoked();

        assertThat(outbox.getStatus()).isEqualTo(AppleRevokeOutboxStatus.REVOKED);
        assertThat(outbox.isPending()).isFalse();
    }

    @Test
    void 실패는_지수_백오프로_재시도하다가_최대_횟수를_넘기면_FAILED로_확정한다() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 0);
        AppleRevokeOutbox outbox = AppleRevokeOutbox.create(1L, "apple-rt", "com.test.app", now);

        outbox.recordFailure(now);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
        assertThat(outbox.getStatus()).isEqualTo(AppleRevokeOutboxStatus.PENDING);

        outbox.recordFailure(now);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(120));

        outbox.recordFailure(now);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(240));

        outbox.recordFailure(now);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(480));
        assertThat(outbox.getStatus()).isEqualTo(AppleRevokeOutboxStatus.PENDING);

        // 5번째 실패(MAX_ATTEMPTS) — 더 이상 재시도하지 않고 FAILED로 확정한다.
        outbox.recordFailure(now);
        assertThat(outbox.getAttemptCount()).isEqualTo(5);
        assertThat(outbox.getStatus()).isEqualTo(AppleRevokeOutboxStatus.FAILED);
        assertThat(outbox.isPending()).isFalse();
    }
}
