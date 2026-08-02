package com.todo.domain.notification.message;

import com.todo.domain.notification.entity.NotificationType;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code notification-messages.yml}의 문구를 바인딩한다.
 *
 * <p>파일은 {@code application.yml}의 {@code spring.config.import}로 읽어들인다.
 */
@ConfigurationProperties(prefix = "notification")
public class NotificationMessageProperties {

    private final Map<NotificationType, NotificationMessageTemplate> messages =
            new EnumMap<>(NotificationType.class);

    public Map<NotificationType, NotificationMessageTemplate> getMessages() {
        return messages;
    }

    public void setMessages(Map<NotificationType, NotificationMessageTemplate> messages) {
        this.messages.putAll(messages);
    }

    /**
     * 모든 알림 종류에 문구가 있는지 기동 시점에 확인한다.
     *
     * <p>없으면 발송 시점에야 NPE로 드러나는데, 알림은 부수 흐름이라 한참 뒤에 발견되기 쉽다.
     * enum에 종류만 추가하고 yml을 빠뜨리는 실수를 기동 실패로 앞당긴다.
     */
    @PostConstruct
    void validateAllTypesDefined() {
        List<NotificationType> missing = Arrays.stream(NotificationType.values())
                .filter(type -> !messages.containsKey(type))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "notification-messages.yml에 문구가 없는 알림 종류: " + missing);
        }
    }
}
