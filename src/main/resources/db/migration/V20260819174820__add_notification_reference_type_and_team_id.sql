ALTER TABLE notifications
    ADD COLUMN reference_type VARCHAR(30) NULL,
    ADD COLUMN team_id BIGINT NULL;

UPDATE notifications
SET reference_type = CASE
    WHEN type IN ('TODO_CREATED', 'TODO_ASSIGNED', 'TODO_UNASSIGNED', 'TODO_SUBMITTED',
                  'TODO_DEADLINE_APPROACHING', 'TODO_WORK_ITEM_EXPIRED', 'TODO_REACTION_ADDED',
                  'TODO_ALL_COMPLETED') THEN 'TODO'
    WHEN type IN ('TEAM_MEMBER_JOINED', 'TEAM_MEMBER_LEFT', 'TEAM_MEMBER_REMOVED', 'TEAM_LEADER_CHANGED') THEN 'TEAM'
    WHEN type = 'AVAILABILITY_POLL_CREATED' THEN 'AVAILABILITY_POLL'
    WHEN type = 'CHAT_MESSAGE' THEN 'CHAT'
    ELSE 'NONE'
END;

ALTER TABLE notifications
    MODIFY COLUMN reference_type VARCHAR(30) NOT NULL;

-- TODO_* 알림: reference_id가 todo id이므로 todos.team_id로 백필한다.
UPDATE notifications n
    JOIN todos t ON n.reference_id = t.id
SET n.team_id = t.team_id
WHERE n.type IN ('TODO_CREATED', 'TODO_ASSIGNED', 'TODO_UNASSIGNED', 'TODO_SUBMITTED',
                  'TODO_DEADLINE_APPROACHING', 'TODO_WORK_ITEM_EXPIRED', 'TODO_REACTION_ADDED',
                  'TODO_ALL_COMPLETED');

-- TEAM_* 알림: reference_id 자체가 이미 team id다.
UPDATE notifications
SET team_id = reference_id
WHERE type IN ('TEAM_MEMBER_JOINED', 'TEAM_MEMBER_LEFT', 'TEAM_MEMBER_REMOVED', 'TEAM_LEADER_CHANGED');

-- AVAILABILITY_POLL_CREATED: reference_id가 poll id이므로 availability_polls.team_id로 백필한다.
UPDATE notifications n
    JOIN availability_polls p ON n.reference_id = p.id
SET n.team_id = p.team_id
WHERE n.type = 'AVAILABILITY_POLL_CREATED';

-- CHAT_MESSAGE는 애초에 저장되지 않고, NEW_DEVICE_LOGIN/PASSWORD_CHANGED는 팀과 무관하므로
-- team_id는 NULL로 남긴다.
