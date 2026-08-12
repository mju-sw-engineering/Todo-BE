-- 탈퇴 직후 시도하는 Apple revoke가 실패했을 때 재시도하기 위한 outbox 테이블.
-- file_deletion_outbox와 달리 최대 시도 횟수를 넘기면 FAILED로 확정하고 재시도를 멈춘다.

CREATE TABLE apple_revoke_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    apple_refresh_token VARCHAR(1024) NOT NULL,
    apple_client_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_apple_revoke_outbox_status_next_attempt (status, next_attempt_at)
) ENGINE=InnoDB;
