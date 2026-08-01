package com.todo.domain.auth.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 비밀번호를 다시 확인했다는 증명. 1회용이며 짧은 시간만 유효하다.
 *
 * <p>원문 토큰은 발급 시 한 번만 응답으로 나가고 여기에는 해시만 남는다.
 * DB가 유출돼도 저장된 값으로는 재인증을 통과할 수 없다.
 */
@Entity
@Table(name = "reauth_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReauthToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReauthPurpose purpose;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    public static ReauthToken create(User user, String tokenHash, ReauthPurpose purpose, LocalDateTime expiresAt) {
        ReauthToken token = new ReauthToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.purpose = purpose;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * 토큰을 소비한다. 이미 쓴 토큰은 다시 소비할 수 없다.
     */
    public void markAsUsed(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public boolean matchesPurpose(ReauthPurpose purpose) {
        return this.purpose == purpose;
    }
}
