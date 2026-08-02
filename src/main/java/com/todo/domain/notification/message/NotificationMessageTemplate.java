package com.todo.domain.notification.message;

import java.util.Map;

/**
 * {@code notification-messages.yml}에 정의된 문구 한 벌.
 *
 * <p>치환은 두 단계로 나뉜다.
 * <ul>
 *   <li><b>발송 시점</b> — {@link #format}이 투두 제목처럼 그때 정해지는 값을 채운다</li>
 *   <li><b>조회 시점</b> — {@link NotificationActorText}가 {@code {actor}}를 현재 닉네임으로 채운다</li>
 * </ul>
 *
 * <p>{@code format}은 인자로 받은 키만 치환하므로 {@code {actor}}는 손대지 않고 남는다.
 * 저장된 문구에 자리표시자가 남아 있어야 닉네임 변경·탈퇴가 나중에 반영된다.
 */
public record NotificationMessageTemplate(
        String title,
        String content
) {

    public NotificationMessage toMessage(
            com.todo.domain.notification.entity.NotificationType type,
            Map<String, String> args
    ) {
        return new NotificationMessage(type, format(title, args), format(content, args));
    }

    private static String format(String template, Map<String, String> args) {
        String result = template;
        for (Map.Entry<String, String> arg : args.entrySet()) {
            result = result.replace("{" + arg.getKey() + "}", arg.getValue() == null ? "" : arg.getValue());
        }
        return result;
    }
}
