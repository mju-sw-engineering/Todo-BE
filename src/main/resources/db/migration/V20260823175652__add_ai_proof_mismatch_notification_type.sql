-- NotificationType에 AI_PROOF_MISMATCH를 추가하면서(인증 파일 AI 판정) notifications.type
-- ENUM 컬럼을 함께 넓히지 않았다. DB가 모르는 값이라 INSERT가 "Data truncated for column
-- 'type'"으로 실패했고, 판정 트랜잭션이 통째로 롤백돼 유료 판정 결과가 매번 사라졌다.
--
-- V20260813202743에서 같은 사고가 이미 한 번 났다. ddl-auto=validate는 ENUM의 허용값까지는
-- 대조하지 않으므로, NotificationType에 값을 추가할 때는 반드시 이 컬럼도 함께 넓힌다.
ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'CHAT_MESSAGE',
        'TODO_CREATED',
        'TODO_ASSIGNED',
        'TODO_UNASSIGNED',
        'TODO_SUBMITTED',
        'TODO_DEADLINE_APPROACHING',
        'TODO_WORK_ITEM_EXPIRED',
        'TODO_REACTION_ADDED',
        'TODO_ALL_COMPLETED',
        'AI_PROOF_MISMATCH',
        'TEAM_MEMBER_JOINED',
        'TEAM_MEMBER_LEFT',
        'TEAM_MEMBER_REMOVED',
        'TEAM_LEADER_CHANGED',
        'NEW_DEVICE_LOGIN',
        'PASSWORD_CHANGED',
        'AVAILABILITY_POLL_CREATED'
    ) NOT NULL;
