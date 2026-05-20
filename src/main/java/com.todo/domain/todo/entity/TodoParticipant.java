package com.todo.domain.todo.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.exception.BusinessException;
import com.todo.domain.todo.entity.VoteType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

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

    private LocalDateTime submittedAt;

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

    public void submit(String proofImageKey) {
        if (this.status != ParticipantStatus.IN_PROGRESS) {
            throw new BusinessException("이미 제출되었거나 완료된 투두입니다.", HttpStatus.CONFLICT);
        }
        this.proofImageKey = proofImageKey;
        this.status = ParticipantStatus.PENDING;
        this.submittedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void addVote(VoteType voteType) {
        if (voteType == VoteType.POSITIVE) {
            this.positiveCount++;
        } else {
            this.negativeCount++;
        }
    }

    public void markAsSuccess() {
        this.status = ParticipantStatus.SUCCESS;
    }

    public void markAsFailByVote() {
        this.status = ParticipantStatus.FAIL;
    }

    public void markAsFail() {
        if (this.status == ParticipantStatus.IN_PROGRESS) {
            this.status = ParticipantStatus.FAIL;
        }
    }
}
