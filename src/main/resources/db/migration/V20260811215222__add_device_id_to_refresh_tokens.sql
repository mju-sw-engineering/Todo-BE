-- 클라이언트가 기기별 고유 ID를 보내면 세션(리프레시 토큰)을 기기 단위로 구분할 수 있다.
-- 프론트/앱이 아직 안 보내는 동안에도 로그인이 막히면 안 되므로 nullable로 둔다.
ALTER TABLE refresh_tokens ADD COLUMN device_id VARCHAR(255) NULL AFTER user_id;
