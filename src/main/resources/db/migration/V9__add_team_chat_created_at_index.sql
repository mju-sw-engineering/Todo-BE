-- 보관 기간이 지난 메시지 정리 스케줄러가 created_at으로 삭제 대상을 찾는다. 없으면 full table scan.
CREATE INDEX idx_tcm_created_at ON team_chat_messages (created_at);
