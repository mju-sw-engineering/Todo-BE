package com.todo.domain.notification.event;

import com.todo.domain.notification.dto.response.NotificationResponse;

/**
 * WebSocket으로 내보낼 알림 한 건. 커밋 후 발송 시점에는 영속성 컨텍스트가 닫혀 있을 수 있어
 * 엔티티가 아니라 발송에 필요한 값만 담는다.
 */
public record NotificationDelivery(String receiverUserId, NotificationResponse payload) {
}
