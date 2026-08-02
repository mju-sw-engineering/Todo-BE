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

    private NotificationMessage build(NotificationType type, Map<String, String> args) {
        return properties.getMessages().get(type).toMessage(type, args);
    }
}
