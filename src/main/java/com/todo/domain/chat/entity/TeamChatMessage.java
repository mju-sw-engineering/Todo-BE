package com.todo.domain.chat.entity;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team_chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = true)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean isBot;

    public static TeamChatMessage create(Team team, User sender, String content) {
        TeamChatMessage message = new TeamChatMessage();
        message.team = team;
        message.sender = sender;
        message.content = content;
        message.isBot = false;
        return message;
    }
}
