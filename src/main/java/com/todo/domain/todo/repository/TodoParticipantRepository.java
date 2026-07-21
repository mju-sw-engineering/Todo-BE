package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.TodoParticipant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TodoParticipantRepository extends JpaRepository<TodoParticipant, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TodoParticipant tp
            SET tp.status = com.todo.domain.todo.entity.ParticipantStatus.FAIL
            WHERE tp.status = com.todo.domain.todo.entity.ParticipantStatus.IN_PROGRESS
              AND tp.todo.id IN (
                  SELECT t.id FROM Todo t WHERE t.deadline < :now
              )
            """)
    int markExpiredParticipantsAsFail(@Param("now") LocalDateTime now);

    @Query("""
            SELECT tp.todo.id AS todoId, tp.user.id AS userId,
                   tp.user.nickname AS nickname, tp.status AS status
            FROM TodoParticipant tp
            WHERE tp.todo.id IN :todoIds
            """)
    List<TodoParticipantSummary> findSummaryByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT tp.id AS todoParticipantId,
                   tp.user.id AS userId, tp.user.nickname AS nickname,
                   tp.user.profileImageUrl AS profileImageUrl,
                   tp.proofImageKey AS proofImageKey,
                   tp.proofThumbnailKey AS proofThumbnailKey, tp.status AS status
            FROM TodoParticipant tp
            WHERE tp.todo.id = :todoId
            """)
    List<TodoParticipantDetail> findDetailByTodoId(@Param("todoId") Long todoId);

    @Query("SELECT tp FROM TodoParticipant tp JOIN FETCH tp.todo WHERE tp.todo.id = :todoId AND tp.user.id = :userId")
    Optional<TodoParticipant> findByTodoIdAndUserIdWithTodo(@Param("todoId") Long todoId, @Param("userId") Long userId);

    @Query("SELECT tp FROM TodoParticipant tp JOIN FETCH tp.todo t JOIN FETCH t.team WHERE tp.id = :participantId")
    Optional<TodoParticipant> findByIdWithTodoAndTeam(@Param("participantId") Long participantId);

    @Query("SELECT tp FROM TodoParticipant tp WHERE tp.todo.id = :todoId AND tp.user.id = :userId")
    Optional<TodoParticipant> findByTodoIdAndUserId(@Param("todoId") Long todoId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tp FROM TodoParticipant tp WHERE tp.todo.id = :todoId AND tp.user.id = :userId")
    Optional<TodoParticipant> findByTodoIdAndUserIdWithLock(@Param("todoId") Long todoId, @Param("userId") Long userId);

    @Query("SELECT COUNT(tp) FROM TodoParticipant tp WHERE tp.todo.id = :todoId")
    long countByTodoId(@Param("todoId") Long todoId);

    @Query("SELECT COUNT(tp) FROM TodoParticipant tp WHERE tp.todo.id = :todoId AND tp.status = :status")
    long countByTodoIdAndStatus(@Param("todoId") Long todoId, @Param("status") ParticipantStatus status);

    @Query("SELECT tp.id FROM TodoParticipant tp WHERE tp.todo.id IN :todoIds")
    List<Long> findIdsByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("SELECT tp.id FROM TodoParticipant tp WHERE tp.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM TodoParticipant tp WHERE tp.todo.id IN :todoIds")
    void deleteByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Modifying
    @Query("DELETE FROM TodoParticipant tp WHERE tp.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
