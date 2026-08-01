package com.todo.domain.todo.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.exception.BusinessException;
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

    /**
     * 참가자가 탈퇴하면 null이 된다. 완료·실패 기록은 팀 달성 이력이므로 상태와 시각만 남기고 익명화한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantStatus status;

    private String proofImageKey;

    private String proofThumbnailKey;

    private LocalDateTime submittedAt;

    public static TodoParticipant create(Todo todo, User user) {
        TodoParticipant participant = new TodoParticipant();
        participant.todo = todo;
        participant.user = user;
        participant.status = ParticipantStatus.IN_PROGRESS;
        return participant;
    }

    public void submit(String proofImageKey) {
        submit(proofImageKey, null);
    }

    public void submit(String proofImageKey, String proofThumbnailKey) {
        if (this.status != ParticipantStatus.IN_PROGRESS) {
            throw new BusinessException("이미 제출되었거나 완료된 투두입니다.", HttpStatus.CONFLICT);
        }
        this.proofImageKey = proofImageKey;
        this.proofThumbnailKey = proofThumbnailKey;
        this.status = ParticipantStatus.SUCCESS;
        this.submittedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void markAsSuccess() {
        this.status = ParticipantStatus.SUCCESS;
    }

    public void markAsFail() {
        if (this.status == ParticipantStatus.IN_PROGRESS) {
            this.status = ParticipantStatus.FAIL;
        }
    }
}
