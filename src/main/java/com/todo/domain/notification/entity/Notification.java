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

    /**
     * referenceId가 가리키는 대상의 종류. 호출부가 직접 넘기지 않고 {@link NotificationType}에서
     * 자동으로 파생시킨다 — 타입과 참조 종류가 어긋나는 조합이 애초에 생길 수 없게 하기 위함.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceType referenceType;

    /**
     * 알림이 속한 팀. referenceId만으로는 프론트가 이동 경로(teamId)를 재구성할 수 없어 별도로 저장한다.
     * 팀과 무관한 알림(NEW_DEVICE_LOGIN, PASSWORD_CHANGED)은 null.
     */
    private Long teamId;

    public static Notification create(
            User receiver,
            User actor,
            NotificationType type,
            String title,
            String content,
            Long referenceId,
            Long teamId
    ) {
        Notification notification = new Notification();
        notification.receiver = receiver;
        notification.actor = actor;
        notification.type = type;
        notification.title = title;
        notification.content = content;
        notification.isRead = false;
        notification.referenceId = referenceId;
        notification.referenceType = type.referenceType();
        notification.teamId = teamId;
        return notification;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
