package com.todo.domain.auth.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "consent_type", "consent_version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private ConsentType consentType;

    @Column(nullable = false)
    private String consentVersion;

    private LocalDateTime revokedAt;

    public static UserConsent create(User user, ConsentType consentType, String consentVersion) {
        UserConsent consent = new UserConsent();
        consent.user = user;
        consent.consentType = consentType;
        consent.consentVersion = consentVersion;
        return consent;
    }

    public void revoke(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isActive() {
        return this.revokedAt == null;
    }
}
