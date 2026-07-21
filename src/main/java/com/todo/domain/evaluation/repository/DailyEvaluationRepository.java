package com.todo.domain.evaluation.repository;

import com.todo.domain.evaluation.entity.DailyEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyEvaluationRepository extends JpaRepository<DailyEvaluation, Long> {

    Optional<DailyEvaluation> findByTeamIdAndEvaluationDate(Long teamId, LocalDate evaluationDate);

    boolean existsByTeamIdAndEvaluationDate(Long teamId, LocalDate evaluationDate);

    @Modifying
    @Query("DELETE FROM DailyEvaluation d WHERE d.team.id = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);
}
