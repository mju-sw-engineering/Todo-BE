package com.todo.global.file.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FileDeletionOutboxTest {

    @Test
    void 생성하면_PENDING_상태로_즉시_삭제_대상이_된다() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);

        FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/1/a.png", now);

        assertThat(outbox.getObjectKey()).isEqualTo("profiles/1/a.png");
        assertThat(outbox.getStatus()).isEqualTo(FileDeletionOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now);
        assertThat(outbox.isPending()).isTrue();
    }

    @Test
    void 삭제에_성공하면_DELETED로_종결한다() {
        FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/1/a.png", LocalDateTime.now());

        outbox.markDeleted();

        assertThat(outbox.getStatus()).isEqualTo(FileDeletionOutboxStatus.DELETED);
        assertThat(outbox.isPending()).isFalse();
    }

    @Test
    void 삭제_실패는_지수_백오프로_계속_재시도한다() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
        FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/1/a.png", now);

        outbox.recordFailure(now);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(30));
        assertThat(outbox.getStatus()).isEqualTo(FileDeletionOutboxStatus.PENDING);

        outbox.recordFailure(now);
        assertThat(outbox.getAttemptCount()).isEqualTo(2);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));

        outbox.recordFailure(now);
        assertThat(outbox.getAttemptCount()).isEqualTo(3);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(120));
        assertThat(outbox.getStatus()).isEqualTo(FileDeletionOutboxStatus.PENDING);

        outbox.recordFailure(now);
        outbox.recordFailure(now);
        outbox.recordFailure(now);
        assertThat(outbox.getAttemptCount()).isEqualTo(6);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(600));
    }
}
