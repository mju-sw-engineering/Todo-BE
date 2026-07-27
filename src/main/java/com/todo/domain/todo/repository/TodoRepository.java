package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Todo t
            SET t.status = com.todo.domain.todo.entity.TodoStatus.FAIL
            WHERE t.status = com.todo.domain.todo.entity.TodoStatus.IN_PROGRESS
              AND t.deadline < :now
            """)
    int markExpiredTodosAsFail(@Param("now") LocalDateTime now);

    @Query("SELECT t.id FROM Todo t WHERE t.team.id = :teamId")
    List<Long> findIdsByTeamId(@Param("teamId") Long teamId);

    @Query("SELECT t.id FROM Todo t WHERE t.creator.id = :creatorId")
    List<Long> findIdsByCreatorId(@Param("creatorId") Long creatorId);

    @Modifying
    @Query("DELETE FROM Todo t WHERE t.team.id = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);

    @Modifying
    @Query("DELETE FROM Todo t WHERE t.id IN :todoIds")
    void deleteByIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("SELECT t FROM Todo t JOIN FETCH t.creator WHERE t.team.id = :teamId ORDER BY t.createdAt DESC")
    List<Todo> findByTeamIdWithCreator(@Param("teamId") Long teamId);

    @Query("""
            SELECT t FROM Todo t
            JOIN FETCH t.creator
            WHERE t.team.id = :teamId
              AND t.status = :status
            ORDER BY t.createdAt DESC
            """)
    List<Todo> findByTeamIdAndStatusWithCreator(
            @Param("teamId") Long teamId,
            @Param("status") TodoStatus status
    );

    @Query("""
            SELECT t FROM Todo t
            JOIN FETCH t.creator
            WHERE t.team.id = :teamId
              AND t.status IN :statuses
            ORDER BY t.createdAt DESC
            """)
    List<Todo> findByTeamIdAndStatusInWithCreator(
            @Param("teamId") Long teamId,
            @Param("statuses") List<TodoStatus> statuses
    );

    @Query("""
            SELECT t FROM Todo t
            JOIN FETCH t.creator
            WHERE t.team.id = :teamId
              AND t.deadline >= :start
              AND t.deadline < :end
            ORDER BY t.deadline ASC
            """)
    List<Todo> findByTeamIdAndDeadlineBetweenWithCreator(
            @Param("teamId") Long teamId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT t FROM Todo t JOIN FETCH t.creator JOIN FETCH t.team WHERE t.id = :todoId")
    Optional<Todo> findByIdWithCreatorAndTeam(@Param("todoId") Long todoId);
}
