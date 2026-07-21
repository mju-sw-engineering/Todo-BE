package com.todo.domain.user.entity;

import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    private String profileImageUrl;

    private String email;

    @Column(nullable = false)
    private boolean termsAgreed = false;

    private LocalDateTime termsAgreedAt;

    public static User create(String loginId, String password, String nickname, String profileImageUrl) {
        User user = new User();
        user.loginId = loginId;
        user.password = password;
        user.nickname = nickname;
        user.profileImageUrl = profileImageUrl;
        return user;
    }

    public void assignEmail(String email) {
        this.email = email;
    }

    public void agreeToTerms(LocalDateTime agreedAt) {
        this.termsAgreed = true;
        this.termsAgreedAt = agreedAt;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
