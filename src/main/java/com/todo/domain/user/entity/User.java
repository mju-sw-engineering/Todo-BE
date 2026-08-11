package com.todo.domain.user.entity;

import com.todo.domain.auth.entity.AuthProvider;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String loginId;

    private String password;

    @Column(nullable = false)
    private String nickname;

    private String profileImageUrl;

    @Column(unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(unique = true)
    private String socialId;

    @Column(columnDefinition = "TEXT")
    private String appleRefreshToken;

    private String appleClientId;

    public static User create(String loginId, String password, String nickname, String profileImageUrl) {
        User user = new User();
        user.loginId = loginId;
        user.password = password;
        user.nickname = nickname;
        user.profileImageUrl = profileImageUrl;
        user.provider = AuthProvider.LOCAL;
        return user;
    }

    public static User createAppleUser(String socialId, String nickname, String profileImageUrl) {
        User user = new User();
        user.socialId = socialId;
        user.nickname = nickname;
        user.profileImageUrl = profileImageUrl;
        user.provider = AuthProvider.APPLE;
        return user;
    }

    public void assignEmail(String email) {
        this.email = email;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void saveAppleCredentials(String appleRefreshToken, String appleClientId) {
        this.appleRefreshToken = appleRefreshToken;
        this.appleClientId = appleClientId;
    }
}
