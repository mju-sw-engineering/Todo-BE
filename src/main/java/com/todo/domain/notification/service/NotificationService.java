package com.todo.domain.notification.service;

import com.todo.domain.notification.dto.response.NotificationPageResponse;
import com.todo.domain.notification.dto.response.NotificationResponse;
import com.todo.domain.notification.dto.response.UnreadNotificationCountResponse;
import com.todo.domain.notification.entity.Notification;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessages;
import com.todo.domain.notification.event.NotificationDelivery;
import com.todo.domain.notification.event.NotificationDispatchEvent;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void send(User receiver, User actor, NotificationMessage message, Long referenceId) {
        sendAll(List.of(receiver), actor, message, referenceId);
    }

    /**
     * 여러 수신자에게 같은 알림을 한 번의 batch insert로 저장한다.
     * 저장은 호출자 트랜잭션에 포함되고, WebSocket 발송은 커밋 후로 미룬다.
     *
     * <p>문구는 {@link NotificationMessages}의 팩토리로 만든다. 행위자 이름은 문구에 직접
     * 넣지 않고 자리표시자로 두며, 여기 넘긴 actor로 조회 시점에 치환된다. 이름을 박아
     * 저장하면 닉네임 변경·탈퇴 후 과거 알림이 옛 이름을 계속 보여준다.
     *
     * @param actor 알림을 유발한 사람. 행위자가 없는 시스템 알림이면 null
     */
    @Transactional
    public void sendAll(List<User> receivers, User actor, NotificationMessage message, Long referenceId) {
        if (receivers.isEmpty()) {
            return;
        }

        List<Notification> notifications = notificationRepository.saveAll(
                receivers.stream()
                        .map(receiver -> Notification.create(
                                receiver, actor, message.type(), message.title(), message.content(), referenceId))
                        .toList()
        );

        List<NotificationDelivery> deliveries = notifications.stream()
                .map(notification -> new NotificationDelivery(
                        notification.getReceiver().getLoginId(),
                        NotificationResponse.from(notification)
                ))
                .toList();

        // 트랜잭션이 없는 컨텍스트에서는 AFTER_COMMIT 이벤트가 발화하지 않으므로 즉시 발송한다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(new NotificationDispatchEvent(deliveries));
        } else {
            dispatch(deliveries);
        }
    }

    /**
     * 알림 행을 남기지 않고 WebSocket 푸시만 한다. 채팅처럼 건수가 많고 보관 가치가 낮은 알림에 쓴다.
     * 미읽음 개수는 도메인 테이블(team_chat_read_statuses)로 계산하므로 알림 테이블에 쌓을 이유가 없다.
     * 수신자는 notificationId가 null인 payload를 받고, 이 알림은 읽음 처리 대상이 아니다.
     *
     * <p>저장하지 않으므로 actor를 받지 않는다. 발송 시점의 닉네임이 곧 현재 닉네임이라
     * 문구가 낡을 수 없다. 문구에 이름을 그대로 넣어도 된다.
     */
    public void pushAll(List<User> receivers, NotificationMessage message, Long referenceId) {
        if (receivers.isEmpty()) {
            return;
        }

        NotificationResponse payload = NotificationResponse.pushOnly(
                message.type(), message.title(), message.content(), referenceId);
        List<NotificationDelivery> deliveries = receivers.stream()
                .map(receiver -> new NotificationDelivery(receiver.getLoginId(), payload))
                .toList();

        // 저장 트랜잭션 안에서 호출되면 롤백된 메시지의 알림이 나가지 않도록 커밋 후로 미룬다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(new NotificationDispatchEvent(deliveries));
        } else {
            dispatch(deliveries);
        }
    }

    /**
     * WebSocket 발송만 담당한다. 실패해도 알림은 이미 저장돼 REST 목록 조회로 복구 가능하므로 로깅만 한다.
     */
    public void dispatch(List<NotificationDelivery> deliveries) {
        for (NotificationDelivery delivery : deliveries) {
            try {
                messagingTemplate.convertAndSendToUser(
                        delivery.receiverLoginId(),
                        "/queue/notifications",
                        delivery.payload()
                );
            } catch (Exception e) {
                log.warn("알림 WebSocket 전송 실패 - receiverLoginId: {}", delivery.receiverLoginId(), e);
            }
        }
    }

    public NotificationPageResponse getNotifications(String loginId, Long cursorId, int size) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        List<Notification> notifications = fetchNotifications(user.getId(), cursorId, size + 1);

        boolean hasNext = notifications.size() > size;
        List<Notification> result = hasNext ? notifications.subList(0, size) : notifications;
        Long nextCursorId = hasNext ? result.get(result.size() - 1).getId() : null;

        List<NotificationResponse> responses = result.stream()
                .map(NotificationResponse::from)
                .toList();

        return new NotificationPageResponse(responses, hasNext, nextCursorId);
    }

    @Transactional
    public void markAsRead(String loginId, Long notificationId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 알림입니다.", HttpStatus.NOT_FOUND));

        if (!notification.getReceiver().getId().equals(user.getId())) {
            throw new BusinessException("알림을 읽음 처리할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        notificationRepository.markAllAsRead(user.getId());
    }

    public UnreadNotificationCountResponse getUnreadCount(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        long count = notificationRepository.countByReceiverIdAndIsRead(user.getId(), false);
        return UnreadNotificationCountResponse.of(count);
    }

    private List<Notification> fetchNotifications(Long receiverId, Long cursorId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        if (cursorId == null) {
            return notificationRepository.findLatestByReceiverId(receiverId, pageRequest);
        }
        return notificationRepository.findByCursorId(receiverId, cursorId, pageRequest);
    }
}
