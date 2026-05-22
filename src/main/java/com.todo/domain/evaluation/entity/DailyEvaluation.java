package com.todo.domain.evaluation.entity;

import com.todo.domain.team.entity.AiPersona;
import com.todo.domain.team.entity.Team;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "daily_evaluations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "evaluation_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyEvaluation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "evaluation_date", nullable = false)
    private LocalDate evaluationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPersona persona;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    public static DailyEvaluation create(Team team, LocalDate evaluationDate, AiPersona persona, String message) {
        DailyEvaluation evaluation = new DailyEvaluation();
        evaluation.team = team;
        evaluation.evaluationDate = evaluationDate;
        evaluation.persona = persona;
        evaluation.message = message;
        return evaluation;
    }

    public void updateEvaluation(AiPersona persona, String message) {
        this.persona = persona;
        this.message = message;
    }
}
