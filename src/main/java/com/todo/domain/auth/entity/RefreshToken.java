package com.todo.domain.auth.entity;

import com.todo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String deviceId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private boolean isUsed = false;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static RefreshToken create(User user, String token, String deviceId, LocalDateTime expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.user = user;
        rt.token = token;
        rt.deviceId = deviceId;
        rt.isUsed = false;
        rt.expiresAt = expiresAt;
        rt.createdAt = LocalDateTime.now();
        return rt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void markAsUsed() {
        this.isUsed = true;
    }
}
