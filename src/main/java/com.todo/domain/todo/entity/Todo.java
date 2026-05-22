package com.todo.domain.todo.entity;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "todos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TodoStatus status;

    public void markAsSuccess() {
        this.status = TodoStatus.SUCCESS;
    }

    public void markAsFail() {
        if (this.status == TodoStatus.IN_PROGRESS) {
            this.status = TodoStatus.FAIL;
        }
    }

    public static Todo create(Team team, User creator, String title, String description, LocalDateTime deadline) {
        Todo todo = new Todo();
        todo.team = team;
        todo.creator = creator;
        todo.title = title;
        todo.description = description;
        todo.deadline = deadline;
        todo.status = TodoStatus.IN_PROGRESS;
        return todo;
    }
}
