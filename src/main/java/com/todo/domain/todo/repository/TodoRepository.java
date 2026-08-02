package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    /**
     * 제출 트랜잭션에서 한 Todo의 동시 제출을 직렬화하기 위한 비관적 락.
     *
     * <p>참가자 행만 잠그면 서로 다른 담당자의 동시 제출이 각자 다른 행을 잠가
     * 아무것도 직렬화되지 않는다. 성공 판정이 참가자 전체를 세는 집계이므로
     * 집계의 기준점인 부모 행을 잠가야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Todo t WHERE t.id = :todoId")
    Optional<Todo> findByIdWithLock(@Param("todoId") Long todoId);

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

    @Modifying
    @Query("DELETE FROM Todo t WHERE t.team.id = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);

    @Modifying
    @Query("DELETE FROM Todo t WHERE t.id IN :todoIds")
    void deleteByIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("SELECT t FROM Todo t LEFT JOIN FETCH t.creator WHERE t.team.id = :teamId ORDER BY t.createdAt DESC")
    List<Todo> findByTeamIdWithCreator(@Param("teamId") Long teamId);

    @Query("""
            SELECT t FROM Todo t
            LEFT JOIN FETCH t.creator
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
            LEFT JOIN FETCH t.creator
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
            LEFT JOIN FETCH t.creator
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

    @Query("SELECT t FROM Todo t LEFT JOIN FETCH t.creator JOIN FETCH t.team WHERE t.id = :todoId")
    Optional<Todo> findByIdWithCreatorAndTeam(@Param("todoId") Long todoId);

    /**
     * 탈퇴자가 생성한 Todo는 팀 공동 기록이므로 삭제하지 않고 생성자만 익명화한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Todo t SET t.creator = null WHERE t.creator.id = :userId")
    int clearCreatorByUserId(@Param("userId") Long userId);

    /**
     * 진행 중 참가 기록이 제거되어 참가자가 0명이 된 Todo를 FAIL로 확정한다.
     * 마감 스케줄러가 이미 확정한 Todo를 되돌리지 않도록 IN_PROGRESS만 대상으로 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Todo t
            SET t.status = com.todo.domain.todo.entity.TodoStatus.FAIL
            WHERE t.id IN :todoIds
              AND t.status = com.todo.domain.todo.entity.TodoStatus.IN_PROGRESS
              AND NOT EXISTS (SELECT 1 FROM TodoParticipant tp WHERE tp.todo.id = t.id)
            """)
    int markAsFailWhenNoParticipantsRemain(@Param("todoIds") List<Long> todoIds);

    /**
     * 진행 중 참가 기록이 제거된 뒤 남은 참가자가 전원 SUCCESS면 Todo를 SUCCESS로 확정한다.
     * 참가자가 0명인 Todo가 "미완료 0건"으로 성공 처리되지 않도록 EXISTS로 잔여 참가자를 요구한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Todo t
            SET t.status = com.todo.domain.todo.entity.TodoStatus.SUCCESS
            WHERE t.id IN :todoIds
              AND t.status = com.todo.domain.todo.entity.TodoStatus.IN_PROGRESS
              AND EXISTS (SELECT 1 FROM TodoParticipant tp WHERE tp.todo.id = t.id)
              AND NOT EXISTS (
                  SELECT 1 FROM TodoParticipant tp
                  WHERE tp.todo.id = t.id
                    AND tp.status <> com.todo.domain.todo.entity.ParticipantStatus.SUCCESS
              )
            """)
    int markAsSuccessWhenRemainingAllSucceeded(@Param("todoIds") List<Long> todoIds);
}
