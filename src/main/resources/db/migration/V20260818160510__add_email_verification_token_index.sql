-- 이메일 인증 토큰 조회에 인덱스를 추가한다.
--
-- 회원가입 완료 시점(validateAndConsume)에만 조회하던 토큰을, 프로필 사진 업로드용
-- presigned URL 발급에서도 조회하게 되면서 호출 빈도가 늘었다. email_verifications는
-- 정리 스케줄러가 없어 계속 쌓이기만 하므로, 인덱스 없이 두면 가입이 늘수록 매 조회가
-- 느려진다.
CREATE INDEX idx_email_verifications_token ON email_verifications (token);
