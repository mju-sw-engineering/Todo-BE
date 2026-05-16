package com.todo.domain.todo.entity;

import com.todo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "todo_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"todo_participant_id", "voter_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_participant_id", nullable = false)
    private TodoParticipant todoParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteType voteType;

    public static TodoVote create(TodoParticipant todoParticipant, User voter, VoteType voteType) {
        TodoVote vote = new TodoVote();
        vote.todoParticipant = todoParticipant;
        vote.voter = voter;
        vote.voteType = voteType;
        return vote;
    }
}
