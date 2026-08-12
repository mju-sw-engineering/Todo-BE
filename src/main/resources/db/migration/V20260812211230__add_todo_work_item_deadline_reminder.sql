-- 마감 30분 전 알림(TODO_DEADLINE_APPROACHING)을 스케줄러가 매 tick마다 중복 발송하지
-- 않도록, 이미 보낸 WorkItem을 표시해둔다. NULL이면 아직 보내지 않은 것이다.
ALTER TABLE todo_work_items
    ADD COLUMN deadline_reminder_sent_at DATETIME(6) NULL;
