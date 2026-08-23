-- NotificationType에 SLASH_COMMAND_RESULT(#193)를 추가한다. notifications.type은 네이티브
-- MySQL ENUM이라(V20260813202743, V20260823175652) DB가 모르는 값을 INSERT하면 저장 시점에
-- 실패한다 — 같은 실수를 세 번째로 반복하지 않으려고 전체 값을 다시 나열한다.
--
-- AI_PROOF_MISMATCH(V20260823175652)도 함께 포함한다 — 이 마이그레이션이 그보다 늦게
-- 적용되므로(타임스탬프 기준) 여기서 빠뜨리면 그 값이 도로 사라진다.
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
        'AVAILABILITY_POLL_CREATED',
        'SLASH_COMMAND_RESULT'
    ) NOT NULL;
