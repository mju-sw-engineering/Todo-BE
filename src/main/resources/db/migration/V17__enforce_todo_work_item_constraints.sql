-- V16의 backfill이 끝난 뒤에만 강화할 수 있는 WorkItem 제약이다.

ALTER TABLE todos
    MODIFY COLUMN mode ENUM('DIRECT', 'TASK') NOT NULL;

ALTER TABLE todo_work_items
    MODIFY COLUMN type ENUM('DIRECT', 'TASK') NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    DROP INDEX uk_todo_participants_todo_id_user_id,
    ADD COLUMN direct_assignee_id BIGINT
        GENERATED ALWAYS AS (IF(type = 'DIRECT', assignee_id, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_todo_work_items_direct (todo_id, direct_assignee_id),
    ADD UNIQUE KEY uk_todo_work_items_proof_image_key (proof_image_key),
    ADD KEY idx_todo_work_items_todo_type_position (todo_id, type, position),
    ADD KEY idx_todo_work_items_assignee_status_deadline (assignee_id, status, deadline),
    ADD KEY idx_todo_work_items_status_deadline (status, deadline);

ALTER TABLE todos
    ADD UNIQUE KEY uk_todos_id_mode (id, mode);

ALTER TABLE todo_work_items
    ADD CONSTRAINT fk_todo_work_items_todo_mode
        FOREIGN KEY (todo_id, type) REFERENCES todos (id, mode);

ALTER TABLE notifications
    MODIFY COLUMN type ENUM('CHAT_MESSAGE', 'TODO_CREATED', 'TODO_ASSIGNED', 'TODO_UNASSIGNED') NOT NULL;

ALTER TABLE teams
    DROP COLUMN consecutive_todo_count;
