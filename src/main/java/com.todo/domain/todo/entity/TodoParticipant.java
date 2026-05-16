package com.todo.domain.todo.entity;

import com.todo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "todo_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"todo_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantStatus status;

    private String proofImageKey;

    @Column(nullable = false)
    private int positiveCount = 0;

    @Column(nullable = false)
    private int negativeCount = 0;

    public static TodoParticipant create(Todo todo, User user) {
        TodoParticipant participant = new TodoParticipant();
        participant.todo = todo;
        participant.user = user;
        participant.status = ParticipantStatus.IN_PROGRESS;
        return participant;
    }
}
