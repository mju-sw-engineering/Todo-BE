package com.todo.domain.notification.message;

import com.todo.domain.notification.entity.NotificationType;

/**
 * 알림 한 건의 종류와 문구.
 *
 * <p>종류와 문구를 한 값으로 묶는다. 호출부가 둘을 따로 넘기면 종류는 {@code TODO_ASSIGNED}인데
 * 문구는 생성 알림인 조합이 만들어질 수 있고, 알림 종류가 늘수록 그 위험이 커진다.
 *
 * <p>인스턴스는 {@link NotificationMessageFactory}로만 만든다.
 *
 * @param title   알림 제목
 * @param content 알림 본문. 행위자 이름은 {@link NotificationActorText#PLACEHOLDER}로 둔다
 */
public record NotificationMessage(
        NotificationType type,
        String title,
        String content
) {
}
