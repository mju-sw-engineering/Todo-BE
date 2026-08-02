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

    /**
     * 생성자가 탈퇴하면 null이 된다. Todo 자체는 팀 공동 기록이므로 삭제하지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TodoMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TodoStatus status;

    /**
     * 실패한 Todo는 남은 WorkItem이 모두 성공하더라도 다시 성공할 수 없다.
     */
    public boolean markAsSuccess() {
        if (this.status != TodoStatus.IN_PROGRESS) {
            return false;
        }
        this.status = TodoStatus.SUCCESS;
        return true;
    }

    public void markAsFail() {
        if (this.status == TodoStatus.IN_PROGRESS) {
            this.status = TodoStatus.FAIL;
        }
    }

    public static Todo create(
            Team team,
            User creator,
            String title,
            String description,
            LocalDateTime deadline,
            TodoMode mode
    ) {
        Todo todo = new Todo();
        todo.team = team;
        todo.creator = creator;
        todo.title = title;
        todo.description = description;
        todo.deadline = deadline;
        todo.mode = mode;
        todo.status = TodoStatus.IN_PROGRESS;
        return todo;
    }

    /**
     * 기존 DIRECT Todo 생성 경로와의 호환용 팩토리다.
     */
    public static Todo create(Team team, User creator, String title, String description, LocalDateTime deadline) {
        return create(team, creator, title, description, deadline, TodoMode.DIRECT);
    }
}
