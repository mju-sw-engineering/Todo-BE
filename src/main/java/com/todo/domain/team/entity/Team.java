package com.todo.domain.team.entity;

import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public void updateTeamImage(String imageKey) {
        this.teamImage = imageKey;
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
