package com.todo.domain.notification.message;

import com.todo.domain.user.entity.User;
import com.todo.domain.user.support.WithdrawnUserDisplay;

/**
 * 알림 문구의 행위자 표기 규칙.
 *
 * <p>닉네임을 문자열로 박아 저장하면 이름을 바꾸거나 탈퇴했을 때 과거 알림이 옛 이름을
 * 계속 보여준다. 저장 시점에는 {@link #PLACEHOLDER}만 남기고 조회 시점에 actor 관계로
 * 현재 닉네임을 채운다.
 *
 * <p>자리표시자가 없는 문구는 그대로 통과시킨다. 이 구조 도입 이전에 저장된 행과,
 * 행위자가 없는 시스템 알림이 여기에 해당한다.
 */
public final class NotificationActorText {

    public static final String PLACEHOLDER = "{actor}";

    private NotificationActorText() {
    }

    public static String render(String template, User actor) {
        if (template == null || !template.contains(PLACEHOLDER)) {
            return template;
        }
        String nickname = actor == null ? WithdrawnUserDisplay.NICKNAME : actor.getNickname();
        return template.replace(PLACEHOLDER, nickname);
    }
}
