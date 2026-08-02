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

    /**
     * 알림을 유발한 사람. 문구에는 닉네임 대신 {@code {actor}} 자리표시자만 저장하고
     * 조회 시점에 이 관계로 현재 닉네임을 치환한다.
     *
     * <p>nullable인 이유가 둘이다. 행위자가 탈퇴하면 관계만 끊고 알림은 보존하며,
     * 마감 임박처럼 행위자가 없는 시스템 알림도 같은 테이블에 담는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

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

    public static Notification create(
            User receiver,
            User actor,
            NotificationType type,
            String title,
            String content,
            Long referenceId
    ) {
        Notification notification = new Notification();
        notification.receiver = receiver;
        notification.actor = actor;
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
