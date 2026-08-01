-- 회원 탈퇴 하드 딜리트 준비:
-- 공동 기록(Todo, 참가 기록)은 삭제하지 않고 작성자 관계만 익명화하기 위해 NOT NULL만 해제한다.
-- FK는 RESTRICT를 유지한다. ON DELETE CASCADE/SET NULL을 붙이면 참조 정리 누락이 조용히 익명화되므로,
-- 앱이 명시적으로 UPDATE/DELETE하고 마지막 users 삭제에서 누락을 FK 위반으로 검출하게 둔다.

ALTER TABLE todos
    MODIFY COLUMN creator_id BIGINT NULL;

-- uk_todo_participants_todo_id_user_id는 유지한다.
-- MySQL unique key는 NULL을 서로 다른 값으로 취급하므로 익명 참가 기록이 한 투두에 여러 건 존재할 수 있다.
ALTER TABLE todo_participants
    MODIFY COLUMN user_id BIGINT NULL;
