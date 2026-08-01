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

    // 탈퇴자의 익명 참가 기록(user = null)이 조회에서 누락되지 않도록 명시적 LEFT JOIN을 사용한다.
    // tp.user.nickname 같은 암묵적 경로 조인은 INNER JOIN으로 번역되어 익명 행을 떨어뜨린다.
    @Query("""
            SELECT tp.todo.id AS todoId, u.id AS userId,
                   u.nickname AS nickname, tp.status AS status
            FROM TodoParticipant tp
            LEFT JOIN tp.user u
            WHERE tp.todo.id IN :todoIds
            """)
    List<TodoParticipantSummary> findSummaryByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT tp.id AS todoParticipantId,
                   u.id AS userId, u.nickname AS nickname,
                   u.profileImageUrl AS profileImageUrl,
                   tp.proofImageKey AS proofImageKey,
                   tp.proofThumbnailKey AS proofThumbnailKey, tp.status AS status
            FROM TodoParticipant tp
            LEFT JOIN tp.user u
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

    @Modifying
    @Query("DELETE FROM TodoParticipant tp WHERE tp.todo.id IN :todoIds")
    void deleteByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    /**
     * 탈퇴자의 진행 중 참가 기록이 걸린 Todo ID. 참가자 제거 후 상태 재평가 대상이 된다.
     */
    @Query("""
            SELECT DISTINCT tp.todo.id FROM TodoParticipant tp
            WHERE tp.user.id = :userId
              AND tp.status = com.todo.domain.todo.entity.ParticipantStatus.IN_PROGRESS
            """)
    List<Long> findTodoIdsByUserIdAndStatusInProgress(@Param("userId") Long userId);

    /**
     * 탈퇴자의 진행 중 참가 기록 ID. 삭제 대상이며, 연결된 반응을 먼저 정리해야 한다.
     */
    @Query("""
            SELECT tp.id FROM TodoParticipant tp
            WHERE tp.user.id = :userId
              AND tp.status = com.todo.domain.todo.entity.ParticipantStatus.IN_PROGRESS
            """)
    List<Long> findInProgressIdsByUserId(@Param("userId") Long userId);

    /**
     * 탈퇴자가 제출한 인증 사진 key. 파일 삭제 대상 수집용이며 원본과 썸네일을 모두 포함한다.
     */
    @Query("""
            SELECT tp.proofImageKey FROM TodoParticipant tp
            WHERE tp.user.id = :userId AND tp.proofImageKey IS NOT NULL
            """)
    List<String> findProofImageKeysByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT tp.proofThumbnailKey FROM TodoParticipant tp
            WHERE tp.user.id = :userId AND tp.proofThumbnailKey IS NOT NULL
            """)
    List<String> findProofThumbnailKeysByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT tp.proofImageKey FROM TodoParticipant tp
            WHERE tp.todo.id IN :todoIds AND tp.proofImageKey IS NOT NULL
            """)
    List<String> findProofImageKeysByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT tp.proofThumbnailKey FROM TodoParticipant tp
            WHERE tp.todo.id IN :todoIds AND tp.proofThumbnailKey IS NOT NULL
            """)
    List<String> findProofThumbnailKeysByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    /**
     * 완료·실패 참가 기록을 익명화한다. 달성 이력이므로 status와 submittedAt은 유지하고,
     * 사용자 관계와 인증 사진 key만 제거한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TodoParticipant tp
            SET tp.user = null, tp.proofImageKey = null, tp.proofThumbnailKey = null
            WHERE tp.user.id = :userId
              AND tp.status <> com.todo.domain.todo.entity.ParticipantStatus.IN_PROGRESS
            """)
    int anonymizeFinishedByUserId(@Param("userId") Long userId);

    /**
     * 진행 중 참가 기록은 아직 실적이 아니므로 배정에서 제거한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM TodoParticipant tp
            WHERE tp.user.id = :userId
              AND tp.status = com.todo.domain.todo.entity.ParticipantStatus.IN_PROGRESS
            """)
    int deleteInProgressByUserId(@Param("userId") Long userId);
}
