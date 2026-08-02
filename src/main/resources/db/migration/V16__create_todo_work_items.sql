-- TodoParticipant를 DIRECT/TASK 실행 단위로 일반화한다.
-- 기존 PK, 인증 사진 key, 반응 행은 모두 유지한다.

RENAME TABLE todo_participants TO todo_work_items;

ALTER TABLE todo_work_items
    CHANGE COLUMN user_id assignee_id BIGINT NULL,
    ADD COLUMN type ENUM('DIRECT', 'TASK') NULL AFTER assignee_id,
    ADD COLUMN task_title VARCHAR(255) NULL AFTER type,
    ADD COLUMN task_description VARCHAR(255) NULL AFTER task_title,
    ADD COLUMN deadline DATETIME(6) NULL AFTER task_description,
    ADD COLUMN position INT NOT NULL DEFAULT 0 AFTER deadline,
    ADD COLUMN created_at DATETIME(6) NULL AFTER submitted_at,
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;

ALTER TABLE todos
    ADD COLUMN mode ENUM('DIRECT', 'TASK') NULL AFTER deadline;

UPDATE todos
SET mode = 'DIRECT';

UPDATE todo_work_items wi
JOIN todos t ON t.id = wi.todo_id
SET wi.type = 'DIRECT',
    wi.deadline = NULL,
    wi.position = 0,
    wi.created_at = COALESCE(wi.submitted_at, t.created_at, NOW(6)),
    wi.updated_at = COALESCE(wi.submitted_at, t.updated_at, NOW(6));

-- FK와 unique key의 명칭도 WorkItem 용어로 바꾼다. 컬럼 rename만으로는 명칭이 바뀌지 않는다.
ALTER TABLE todo_reactions
    DROP FOREIGN KEY fk_todo_reactions_todo_participant,
    DROP INDEX uk_todo_reactions_todo_participant_id_user_id,
    CHANGE COLUMN todo_participant_id todo_work_item_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_todo_reactions_todo_work_item_id_user_id (todo_work_item_id, user_id),
    ADD CONSTRAINT fk_todo_reactions_todo_work_item
        FOREIGN KEY (todo_work_item_id) REFERENCES todo_work_items (id);
