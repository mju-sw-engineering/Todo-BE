package com.todo.domain.notification.event;

import java.util.List;

/**
 * 알림이 저장됐음을 알리는 이벤트. 저장 트랜잭션 커밋 후 WebSocket 발송을 트리거하는 데 쓰인다.
 */
public record NotificationDispatchEvent(List<NotificationDelivery> deliveries) {
}
