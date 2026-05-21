package com.todo.domain.evaluation.repository;

import com.todo.domain.evaluation.entity.DailyEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyEvaluationRepository extends JpaRepository<DailyEvaluation, Long> {

    Optional<DailyEvaluation> findByTeamIdAndEvaluationDate(Long teamId, LocalDate evaluationDate);

    boolean existsByTeamIdAndEvaluationDate(Long teamId, LocalDate evaluationDate);
}
