package com.todo.domain.team.entity;

import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String teamName;

    @Column(length = 255)
    private String description;

    private String teamImage;

    @Column(nullable = false, unique = true)
    private String inviteCode;

    @Column(nullable = false)
    private int successCount = 0;

    @Column(unique = true)
    private String inviteLinkToken;

    private LocalDateTime inviteLinkExpiresAt;

    public void updateTeamImage(String imageKey) {
        this.teamImage = imageKey;
    }

    /**
     * inviteCode(영구)와 별도의 값이다. 공유 링크는 카카오톡/문자 등으로 퍼지기 쉬워
     * 노출 범위가 크므로 만료·재발급이 필요하다.
     */
    public void updateInviteLink(String token, LocalDateTime expiresAt) {
        this.inviteLinkToken = token;
        this.inviteLinkExpiresAt = expiresAt;
    }

    public boolean hasValidInviteLink(LocalDateTime now) {
        return inviteLinkToken != null && inviteLinkExpiresAt != null && now.isBefore(inviteLinkExpiresAt);
    }

    public static Team create(String teamName, String teamImage, String inviteCode) {
        return create(teamName, null, teamImage, inviteCode);
    }

    public static Team create(String teamName, String description, String teamImage, String inviteCode) {
        Team team = new Team();
        team.teamName = teamName;
        team.description = description;
        team.teamImage = teamImage;
        team.inviteCode = inviteCode;
        return team;
    }
}
