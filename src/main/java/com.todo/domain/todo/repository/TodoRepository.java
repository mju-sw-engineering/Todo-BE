package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    @Query("SELECT t FROM Todo t JOIN FETCH t.creator WHERE t.team.id = :teamId ORDER BY t.createdAt DESC")
    List<Todo> findByTeamIdWithCreator(@Param("teamId") Long teamId);

    @Query("SELECT t FROM Todo t JOIN FETCH t.creator JOIN FETCH t.team WHERE t.id = :todoId")
    Optional<Todo> findByIdWithCreatorAndTeam(@Param("todoId") Long todoId);

    @Query("""
            SELECT t.title AS title,
                   t.status AS status,
                   t.deadline AS deadline,
                   SUM(CASE WHEN tp.status = com.todo.domain.todo.entity.ParticipantStatus.SUCCESS THEN 1 ELSE 0 END) AS achievementCount,
                   COUNT(tp.id) AS participantCount
            FROM Todo t
            LEFT JOIN TodoParticipant tp ON tp.todo = t
            WHERE t.team.id = :teamId
              AND t.deadline >= :start
              AND t.deadline < :end
            GROUP BY t.id, t.title, t.status, t.deadline
            ORDER BY t.deadline ASC
            """)
    List<TodoDailyEvaluationStat> findDailyEvaluationStats(
            @Param("teamId") Long teamId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
