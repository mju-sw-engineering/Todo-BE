package com.todo.domain.notification.entity;

public enum NotificationType {
    CHAT_MESSAGE(ReferenceType.CHAT),
    TODO_CREATED(ReferenceType.TODO),
    TODO_ASSIGNED(ReferenceType.TODO),
    TODO_UNASSIGNED(ReferenceType.TODO),
    TODO_SUBMITTED(ReferenceType.TODO),
    TODO_DEADLINE_APPROACHING(ReferenceType.TODO),
    TODO_WORK_ITEM_EXPIRED(ReferenceType.TODO),
    TODO_REACTION_ADDED(ReferenceType.TODO),
    TODO_ALL_COMPLETED(ReferenceType.TODO),
    /** AI 판정이 제출물과 할 일의 불일치를 알림. 제출자 본인에게만 발송한다. */
    AI_PROOF_MISMATCH(ReferenceType.TODO),
    TEAM_MEMBER_JOINED(ReferenceType.TEAM),
    TEAM_MEMBER_LEFT(ReferenceType.TEAM),
    TEAM_MEMBER_REMOVED(ReferenceType.TEAM),
    TEAM_LEADER_CHANGED(ReferenceType.TEAM),
    NEW_DEVICE_LOGIN(ReferenceType.NONE),
    PASSWORD_CHANGED(ReferenceType.NONE),
    AVAILABILITY_POLL_CREATED(ReferenceType.AVAILABILITY_POLL),
    SLASH_COMMAND_RESULT(ReferenceType.CHAT);

    private final ReferenceType referenceType;

    NotificationType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public ReferenceType referenceType() {
        return referenceType;
    }
}
