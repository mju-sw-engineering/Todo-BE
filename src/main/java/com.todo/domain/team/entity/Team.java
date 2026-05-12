package com.todo.domain.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String teamName;

    private String teamImage;

    @Column(nullable = false, unique = true)
    private String inviteCode;

    @Column(nullable = false)
    private int successCount = 0;

    @Column(nullable = false)
    private int consecutiveTodoCount = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void updateTeamImage(String imageKey) {
        this.teamImage = imageKey;
    }

    public static Team create(String teamName, String teamImage, String inviteCode) {
        Team team = new Team();
        team.teamName = teamName;
        team.teamImage = teamImage;
        team.inviteCode = inviteCode;
        return team;
    }
}
