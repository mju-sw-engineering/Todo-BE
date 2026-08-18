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

    @Column(nullable = false)
    private int attemptCount = 0;

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

    public void increaseAttempt() {
        this.attemptCount++;
    }

    public boolean isAttemptExceeded(int maxAttempts) {
        return this.attemptCount >= maxAttempts;
    }

    /**
     * 코드 검증 성공 시 토큰을 발급하고 만료를 토큰 단계 수명으로 재부여한다.
     * 연장하지 않으면 코드 만료(3분)가 그대로 남아 가입 완료 전에 토큰이 죽는다.
     */
    public void verify(String token, LocalDateTime tokenExpiresAt) {
        this.token = token;
        this.expiresAt = tokenExpiresAt;
    }

    public void markAsUsed() {
        this.used = true;
    }
}
