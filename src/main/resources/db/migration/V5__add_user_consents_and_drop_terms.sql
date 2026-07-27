CREATE TABLE user_consents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    consent_type    VARCHAR(20)  NOT NULL,
    consent_version VARCHAR(10)  NOT NULL,
    revoked_at      DATETIME,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_consents_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_user_consent UNIQUE (user_id, consent_type, consent_version)
);

ALTER TABLE users DROP COLUMN terms_agreed;
ALTER TABLE users DROP COLUMN terms_agreed_at;
