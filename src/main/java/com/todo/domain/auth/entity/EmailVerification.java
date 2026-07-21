package com.todo.domain.auth.entity;

import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String code;

    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    public static EmailVerification create(String email, String code, LocalDateTime expiresAt) {
        EmailVerification ev = new EmailVerification();
        ev.email = email;
        ev.code = code;
        ev.expiresAt = expiresAt;
        return ev;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void verify(String token) {
        this.token = token;
    }

    public void markAsUsed() {
        this.used = true;
    }
}
