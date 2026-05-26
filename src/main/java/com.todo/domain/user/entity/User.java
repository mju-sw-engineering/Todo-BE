package com.todo.domain.user.entity;

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

    @Column(unique = true, nullable = false)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    private String profileImageUrl;

    public static User create(String loginId, String password, String nickname, String profileImageUrl) {
        User user = new User();
        user.loginId = loginId;
        user.password = password;
        user.nickname = nickname;
        user.profileImageUrl = profileImageUrl;
        return user;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
