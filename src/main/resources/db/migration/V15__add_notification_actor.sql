-- 알림 문구에 행위자 닉네임을 문자열로 박아 저장하던 것을 actor 참조로 바꾼다.
-- 저장 문구에는 {actor} 자리표시자만 남기고 조회 시점에 현재 닉네임으로 치환한다.
--
-- FK는 다른 사용자 참조와 같이 RESTRICT를 유지한다. ON DELETE SET NULL을 붙이면
-- 탈퇴 시 정리 누락이 조용히 익명화되므로, 앱이 명시적으로 UPDATE하고
-- 마지막 users 삭제에서 누락을 FK 위반으로 검출하게 둔다.
--
-- 기존 행은 actor_id = NULL로 남는다. 문구에 자리표시자가 없어 치환 대상이 없으므로
-- 저장된 문구가 그대로 조회된다. 백필하지 않는다.
ALTER TABLE notifications
    ADD COLUMN actor_id BIGINT NULL,
    ADD CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users (id);

CREATE INDEX idx_notifications_actor_id ON notifications (actor_id);
