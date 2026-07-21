package com.todo.domain.notification.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean isRead;

    private Long referenceId;

    public static Notification create(User receiver, NotificationType type, String title, String content, Long referenceId) {
        Notification notification = new Notification();
        notification.receiver = receiver;
        notification.type = type;
        notification.title = title;
        notification.content = content;
        notification.isRead = false;
        notification.referenceId = referenceId;
        return notification;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
