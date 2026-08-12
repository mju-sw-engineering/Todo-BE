-- 공유 링크(카카오톡/문자 등)로 퍼지는 값이라 노출 위험이 커서, 영구적인 invite_code와 별도로
-- 만료·재발급되는 값을 둔다. 아직 발급 전인 팀은 NULL이라 nullable로 둔다.
ALTER TABLE teams
    ADD COLUMN invite_link_token VARCHAR(255) NULL,
    ADD COLUMN invite_link_expires_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_teams_invite_link_token UNIQUE (invite_link_token);
