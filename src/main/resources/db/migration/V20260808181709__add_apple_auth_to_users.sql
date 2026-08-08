-- Apple 소셜 로그인 지원을 위한 users 테이블 확장.
-- login_id/password를 nullable로 전환하고 provider, social_id, apple_refresh_token 추가.

ALTER TABLE users
    MODIFY COLUMN login_id VARCHAR(255) NULL,
    MODIFY COLUMN password VARCHAR(255) NULL,
    ADD COLUMN provider       ENUM('LOCAL', 'APPLE') NOT NULL DEFAULT 'LOCAL' AFTER password,
    ADD COLUMN social_id      VARCHAR(255)                     NULL          AFTER provider,
    ADD COLUMN apple_refresh_token TEXT                        NULL          AFTER social_id;

CREATE UNIQUE INDEX uk_users_social_id ON users (social_id);
