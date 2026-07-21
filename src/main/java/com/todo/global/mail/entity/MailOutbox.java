package com.todo.global.mail.entity;

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
@Table(name = "mail_outbox",
        indexes = @Index(name = "idx_mail_outbox_status_next_attempt", columnList = "status, next_attempt_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailOutbox extends BaseTimeEntity {

    private static final long BASE_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 600;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private MailType type;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String textBody;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String htmlBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private MailOutboxStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private int maxAttempts;

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    public static MailOutbox create(
            MailType type,
            String recipient,
            String subject,
            String textBody,
            String htmlBody,
            LocalDateTime expiresAt,
            int maxAttempts,
            LocalDateTime now
    ) {
        MailOutbox outbox = new MailOutbox();
        outbox.type = type;
        outbox.recipient = recipient;
        outbox.subject = subject;
        outbox.textBody = textBody;
        outbox.htmlBody = htmlBody;
        outbox.expiresAt = expiresAt;
        outbox.maxAttempts = maxAttempts;
        outbox.status = MailOutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = now;
        return outbox;
    }

    public boolean isPending() {
        return status == MailOutboxStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public void markSent() {
        this.status = MailOutboxStatus.SENT;
    }

    public void markExpired() {
        this.status = MailOutboxStatus.FAILED;
    }

    /**
     * 발송 실패 시 재시도 횟수를 늘리고, 최대 시도를 넘으면 FAILED로 종결한다.
     * 그렇지 않으면 지수 백오프로 다음 시도 시각을 미룬다.
     */
    public void recordFailure(LocalDateTime now) {
        this.attemptCount++;
        if (this.attemptCount >= this.maxAttempts) {
            this.status = MailOutboxStatus.FAILED;
            return;
        }
        this.nextAttemptAt = now.plusSeconds(backoffSeconds());
    }

    private long backoffSeconds() {
        long factor = 1L << Math.min(attemptCount - 1, 20);
        return Math.min(BASE_BACKOFF_SECONDS * factor, MAX_BACKOFF_SECONDS);
    }
}
