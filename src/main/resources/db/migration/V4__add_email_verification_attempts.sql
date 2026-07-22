-- 인증 코드 무차별 대입을 막기 위한 시도 횟수 컬럼. 기존 행은 0으로 시작한다.

ALTER TABLE email_verifications
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
