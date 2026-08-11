-- 비밀번호를 잊은 상태(로그인 불가)에서 이메일 인증만으로 발급받는 1회용 비밀번호 재설정 토큰.
--
-- reauth_tokens와 구조는 같지만 분리한다: reauth_tokens는 "이미 로그인된 사람이 다시
-- 본인임을 증명"하는 용도라 재발급 시점에 이미 인증된 신원이 있다는 게 전제다. 이 토큰은
-- 로그인 자체가 안 된 사람이 이메일 인증만으로 받는 것이라 전제가 다르다.
--
-- token_hash만 저장한다. 원문은 발급 응답으로 한 번만 나가며 서버에 남기지 않는다.
CREATE TABLE password_reset_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_tokens_token_hash (token_hash),
    KEY idx_password_reset_tokens_expires_at (expires_at),
    -- 탈퇴 시 users 삭제보다 먼저 정리해야 한다. RESTRICT를 유지해 누락을 드러낸다.
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
