-- NotificationType.java에 알림 종류가 16개로 늘었지만(cd6498a, b77a650) notifications.type
-- MySQL ENUM 컬럼은 V17에서 고정한 4개 값에 머물러 있었다. DB가 모르는 값을 INSERT하면
-- 저장 시점에 실패하는데, 팀 참여 직후 TEAM_MEMBER_JOINED 알림 저장이 여기 걸려 팀 참여
-- API 전체가 500으로 롤백됐다.
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
        'TEAM_MEMBER_JOINED',
        'TEAM_MEMBER_LEFT',
        'TEAM_MEMBER_REMOVED',
        'TEAM_LEADER_CHANGED',
        'NEW_DEVICE_LOGIN',
        'PASSWORD_CHANGED',
        'AVAILABILITY_POLL_CREATED'
    ) NOT NULL;
