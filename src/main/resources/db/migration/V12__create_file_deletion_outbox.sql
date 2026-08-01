-- DB 데이터 삭제와 S3 오브젝트 삭제 사이의 정합성을 보장하기 위한 outbox 테이블.

CREATE TABLE file_deletion_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(1024) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_file_deletion_outbox_status_next_attempt (status, next_attempt_at)
) ENGINE=InnoDB;
