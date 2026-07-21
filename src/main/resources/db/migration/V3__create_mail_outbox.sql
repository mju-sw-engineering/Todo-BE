-- 메일 발송을 outbox 패턴으로 비동기 처리하기 위한 테이블.

CREATE TABLE mail_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    text_body TEXT NOT NULL,
    html_body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL,
    max_attempts INT NOT NULL,
    expires_at DATETIME(6),
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_mail_outbox_status_next_attempt (status, next_attempt_at)
) ENGINE=InnoDB;
