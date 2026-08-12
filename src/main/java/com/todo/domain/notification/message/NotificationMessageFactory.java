package com.todo.domain.notification.message;

import com.todo.domain.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 알림 문구를 만든다. 문구 자체는 {@code notification-messages.yml}에 있고 여기서는 값만 채운다.
 *
 * <p>문구를 고치는 일은 yml만 건드리면 되지만 팩토리 메서드는 Java에 남긴다.
 * 호출부가 {@code Map.of("todoTitle", ...)} 같은 맵을 직접 넘기면 키 오타가 컴파일 시점에
 * 걸리지 않고, 어떤 값이 필요한지도 yml을 열어봐야 알 수 있다. 메서드 시그니처가 그 계약을 대신한다.
 *
 * <p>도메인 엔티티를 파라미터로 받지 않는다. 알림 도메인이 투두·채팅을 알게 되면 의존 방향이 뒤집힌다.
 * referenceId는 문구가 아니라 이동 대상이므로 발송 시점에 따로 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class NotificationMessageFactory {

    private final NotificationMessageProperties properties;

    /**
     * 팀에 새 투두가 생성됨. 생성자를 제외한 팀원에게 발송한다.
     */
    public NotificationMessage todoCreated(String todoTitle) {
        return build(NotificationType.TODO_CREATED, Map.of("todoTitle", todoTitle));
    }

    /**
     * 한 Todo 안에서 담당자에게 할 일이 배정됨. 여러 건도 Todo당 한 알림으로 묶는다.
     */
    public NotificationMessage todoAssigned(String todoTitle, int taskCount) {
        return build(NotificationType.TODO_ASSIGNED, Map.of(
                "todoTitle", todoTitle,
                "taskCount", String.valueOf(taskCount)
        ));
    }

    /**
     * 담당자 이탈로 한 Todo의 할 일이 미배정 상태가 됨. 여러 건도 Todo당 한 알림으로 묶는다.
     */
    public NotificationMessage todoUnassigned(String todoTitle, int taskCount) {
        return build(NotificationType.TODO_UNASSIGNED, Map.of(
                "todoTitle", todoTitle,
                "taskCount", String.valueOf(taskCount)
        ));
    }

    /**
     * 팀 채팅 메시지 도착. push 전용이라 저장되지 않으므로 발신자 닉네임을 그대로 채운다.
     */
    public NotificationMessage chatMessage(String senderNickname, String content) {
        return build(NotificationType.CHAT_MESSAGE, Map.of(
                "senderNickname", senderNickname,
                "content", content
        ));
    }

    /**
     * 인증 사진(작업 완료 증거)이 제출됨. 제출자를 제외한 팀 전체에게 발송한다.
     */
    public NotificationMessage todoSubmitted(String todoTitle) {
        return build(NotificationType.TODO_SUBMITTED, Map.of("todoTitle", todoTitle));
    }

    /**
     * WorkItem 마감 30분 전. 담당자에게 발송한다.
     */
    public NotificationMessage todoDeadlineApproaching(String todoTitle) {
        return build(NotificationType.TODO_DEADLINE_APPROACHING, Map.of("todoTitle", todoTitle));
    }

    /**
     * WorkItem이 마감을 넘겨 실패 처리됨. 담당자에게 발송한다.
     */
    public NotificationMessage todoWorkItemExpired(String todoTitle) {
        return build(NotificationType.TODO_WORK_ITEM_EXPIRED, Map.of("todoTitle", todoTitle));
    }

    /**
     * 제출물에 이모지 반응이 남음. 제출자에게 발송한다.
     */
    public NotificationMessage todoReactionAdded(String todoTitle) {
        return build(NotificationType.TODO_REACTION_ADDED, Map.of("todoTitle", todoTitle));
    }

    /**
     * 한 Todo 안의 모든 WorkItem이 성공으로 끝남. 팀 전체에게 발송한다.
     */
    public NotificationMessage todoAllCompleted(String todoTitle) {
        return build(NotificationType.TODO_ALL_COMPLETED, Map.of("todoTitle", todoTitle));
    }

    /**
     * 새 팀원이 합류함. 기존 팀원에게 발송한다.
     */
    public NotificationMessage teamMemberJoined() {
        return build(NotificationType.TEAM_MEMBER_JOINED, Map.of());
    }

    /**
     * 팀원이 스스로 나감(자발적 탈퇴 또는 회원 탈퇴). 남은 팀원에게 발송한다.
     */
    public NotificationMessage teamMemberLeft() {
        return build(NotificationType.TEAM_MEMBER_LEFT, Map.of());
    }

    /**
     * 팀원이 강퇴됨. 강퇴당한 사람에게 발송한다.
     */
    public NotificationMessage teamMemberRemoved() {
        return build(NotificationType.TEAM_MEMBER_REMOVED, Map.of());
    }

    /**
     * 팀장 탈퇴로 권한이 자동 이양됨. 새 팀장에게 발송한다.
     */
    public NotificationMessage teamLeaderChanged() {
        return build(NotificationType.TEAM_LEADER_CHANGED, Map.of());
    }

    /**
     * 등록되지 않은 기기에서 로그인됨. 본인에게 발송한다.
     */
    public NotificationMessage newDeviceLogin() {
        return build(NotificationType.NEW_DEVICE_LOGIN, Map.of());
    }

    /**
     * 비밀번호가 변경됨(로그인 상태 변경 또는 이메일 재설정). 본인에게 발송한다.
     */
    public NotificationMessage passwordChanged() {
        return build(NotificationType.PASSWORD_CHANGED, Map.of());
    }

    /**
     * 회의시간 투표가 생성됨. 생성자를 제외한 팀원에게 발송한다.
     */
    public NotificationMessage availabilityPollCreated(String pollTitle) {
        return build(NotificationType.AVAILABILITY_POLL_CREATED, Map.of("pollTitle", pollTitle));
    }

    private NotificationMessage build(NotificationType type, Map<String, String> args) {
        return properties.getMessages().get(type).toMessage(type, args);
    }
}
