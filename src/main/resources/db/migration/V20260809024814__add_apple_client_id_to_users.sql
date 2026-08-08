-- Apple 탈퇴 시 revoke 요청에 필요한 client_id를 로그인 시점에 저장해둔다.
-- Apple revoke API는 토큰 발급에 쓴 client_id와 요청 client_id가 일치해야 한다.

ALTER TABLE users
    ADD COLUMN apple_client_id VARCHAR(255) NULL AFTER apple_refresh_token;
