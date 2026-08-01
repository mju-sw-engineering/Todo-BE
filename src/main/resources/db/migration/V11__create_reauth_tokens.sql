-- 민감한 작업(회원 탈퇴, 이후의 비밀번호·이메일 변경) 직전에 비밀번호를 다시 확인했다는 증명.
--
-- JWT가 아니라 DB에 두는 이유:
--   1. 1회용 보장 — 상태가 없으면 같은 토큰을 여러 번 쓸 수 있다.
--   2. 폐기 가능 — 발급 후 취소할 방법이 필요하다.
--   3. JwtUtil을 건드리지 않는다.
--
-- token_hash만 저장한다. 원문은 발급 응답으로 한 번만 나가며 서버에 남기지 않는다.
CREATE TABLE reauth_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    purpose    VARCHAR(30)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reauth_tokens_token_hash (token_hash),
    KEY idx_reauth_tokens_expires_at (expires_at),
    -- 탈퇴 시 users 삭제보다 먼저 정리해야 한다. RESTRICT를 유지해 누락을 드러낸다.
    CONSTRAINT fk_reauth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
