package com.todo.domain.notification.service;

import com.todo.domain.notification.dto.response.NotificationPageResponse;
import com.todo.domain.notification.dto.response.NotificationResponse;
import com.todo.domain.notification.dto.response.UnreadNotificationCountResponse;
import com.todo.domain.notification.entity.Notification;
import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void send(User receiver, NotificationType type, String title, String content, Long referenceId) {
        Notification notification = notificationRepository.save(
                Notification.create(receiver, type, title, content, referenceId)
        );
        try {
            messagingTemplate.convertAndSendToUser(
                    receiver.getLoginId(),
                    "/queue/notifications",
                    NotificationResponse.from(notification)
            );
        } catch (Exception e) {
            log.warn("알림 WebSocket 전송 실패 - receiverId: {}", receiver.getId(), e);
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
