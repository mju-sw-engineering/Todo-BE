package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TodoWorkItemRepository extends JpaRepository<TodoWorkItem, Long> {

    @Query("""
            SELECT DISTINCT wi.todo.id FROM TodoWorkItem wi
            WHERE wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
              AND COALESCE(wi.deadline, wi.todo.deadline) < :now
            """)
    List<Long> findOverdueTodoIds(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TodoWorkItem wi
            SET wi.status = com.todo.domain.todo.entity.WorkItemStatus.FAIL
            WHERE wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
              AND COALESCE(wi.deadline, wi.todo.deadline) < :now
            """)
    int markOverdueAsFail(@Param("now") LocalDateTime now);

    @Query("""
            SELECT wi.todo.id AS todoId, a.id AS assigneeId,
                   a.nickname AS nickname, wi.status AS status,
                   wi.type AS type, wi.position AS position
            FROM TodoWorkItem wi
            LEFT JOIN wi.assignee a
            WHERE wi.todo.id IN :todoIds
            ORDER BY wi.todo.id, wi.type, wi.position, wi.id
            """)
    List<TodoWorkItemSummary> findSummaryByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT wi.id AS todoWorkItemId,
                   a.id AS assigneeId, a.nickname AS nickname,
                   a.profileImageUrl AS profileImageUrl,
                   wi.type AS type, wi.taskTitle AS taskTitle,
                   wi.taskDescription AS taskDescription, wi.deadline AS deadline,
                   wi.position AS position, wi.proofImageKey AS proofImageKey,
                   wi.proofThumbnailKey AS proofThumbnailKey, wi.submittedAt AS submittedAt,
                   wi.status AS status
            FROM TodoWorkItem wi
            LEFT JOIN wi.assignee a
            WHERE wi.todo.id = :todoId
            ORDER BY wi.type, wi.position, wi.id
            """)
    List<TodoWorkItemDetail> findDetailByTodoId(@Param("todoId") Long todoId);

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            JOIN FETCH wi.todo
            LEFT JOIN FETCH wi.assignee
            WHERE wi.todo.id = :todoId
            ORDER BY wi.position, wi.id
            """)
    List<TodoWorkItem> findByTodoIdOrderByPositionAsc(@Param("todoId") Long todoId);

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            JOIN FETCH wi.todo
            LEFT JOIN FETCH wi.assignee
            WHERE wi.todo.id IN :todoIds
            ORDER BY wi.todo.id, wi.position, wi.id
            """)
    List<TodoWorkItem> findByTodoIdInOrderByTodoIdAndPosition(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            JOIN FETCH wi.todo t
            LEFT JOIN FETCH wi.assignee
            WHERE t.team.id = :teamId
              AND COALESCE(wi.deadline, t.deadline) >= :start
              AND COALESCE(wi.deadline, t.deadline) < :end
            ORDER BY COALESCE(wi.deadline, t.deadline), wi.position, wi.id
            """)
    List<TodoWorkItem> findByTeamIdAndEffectiveDeadlineBetween(
            @Param("teamId") Long teamId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    boolean existsByProofImageKey(String proofImageKey);

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            JOIN FETCH wi.todo
            WHERE wi.todo.id = :todoId
              AND wi.assignee.id = :assigneeId
              AND wi.type = com.todo.domain.todo.entity.WorkItemType.DIRECT
            """)
    Optional<TodoWorkItem> findDirectByTodoIdAndAssigneeIdWithTodo(
            @Param("todoId") Long todoId,
            @Param("assigneeId") Long assigneeId
    );

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            JOIN FETCH wi.todo t
            JOIN FETCH t.team
            LEFT JOIN FETCH wi.assignee
            WHERE wi.id = :workItemId
            """)
    Optional<TodoWorkItem> findByIdWithTodoAndTeam(@Param("workItemId") Long workItemId);

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            WHERE wi.todo.id = :todoId
              AND wi.assignee.id = :assigneeId
              AND wi.type = com.todo.domain.todo.entity.WorkItemType.DIRECT
            """)
    Optional<TodoWorkItem> findDirectByTodoIdAndAssigneeId(
            @Param("todoId") Long todoId,
            @Param("assigneeId") Long assigneeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT wi FROM TodoWorkItem wi
            WHERE wi.todo.id = :todoId
              AND wi.assignee.id = :assigneeId
              AND wi.type = com.todo.domain.todo.entity.WorkItemType.DIRECT
            """)
    Optional<TodoWorkItem> findDirectByTodoIdAndAssigneeIdWithLock(
            @Param("todoId") Long todoId,
            @Param("assigneeId") Long assigneeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wi FROM TodoWorkItem wi WHERE wi.id = :workItemId")
    Optional<TodoWorkItem> findByIdWithLock(@Param("workItemId") Long workItemId);

    @Query("SELECT COUNT(wi) FROM TodoWorkItem wi WHERE wi.todo.id = :todoId")
    long countByTodoId(@Param("todoId") Long todoId);

    @Query("SELECT COUNT(wi) FROM TodoWorkItem wi WHERE wi.todo.id = :todoId AND wi.status = :status")
    long countByTodoIdAndStatus(@Param("todoId") Long todoId, @Param("status") WorkItemStatus status);

    @Query("SELECT wi.id FROM TodoWorkItem wi WHERE wi.todo.id IN :todoIds")
    List<Long> findIdsByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Modifying
    @Query("DELETE FROM TodoWorkItem wi WHERE wi.todo.id IN :todoIds")
    void deleteByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT DISTINCT wi.todo.id FROM TodoWorkItem wi
            WHERE wi.assignee.id = :userId
              AND wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
            """)
    List<Long> findTodoIdsByAssigneeIdAndStatusInProgress(@Param("userId") Long userId);

    @Query("""
            SELECT wi.id FROM TodoWorkItem wi
            WHERE wi.assignee.id = :userId
              AND wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
            """)
    List<Long> findInProgressIdsByAssigneeId(@Param("userId") Long userId);

    @Query("""
            SELECT wi.proofImageKey FROM TodoWorkItem wi
            WHERE wi.assignee.id = :userId AND wi.proofImageKey IS NOT NULL
            """)
    List<String> findProofImageKeysByAssigneeId(@Param("userId") Long userId);

    @Query("""
            SELECT wi.proofThumbnailKey FROM TodoWorkItem wi
            WHERE wi.assignee.id = :userId AND wi.proofThumbnailKey IS NOT NULL
            """)
    List<String> findProofThumbnailKeysByAssigneeId(@Param("userId") Long userId);

    @Query("""
            SELECT wi.proofImageKey FROM TodoWorkItem wi
            WHERE wi.todo.id IN :todoIds AND wi.proofImageKey IS NOT NULL
            """)
    List<String> findProofImageKeysByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT wi.proofThumbnailKey FROM TodoWorkItem wi
            WHERE wi.todo.id IN :todoIds AND wi.proofThumbnailKey IS NOT NULL
            """)
    List<String> findProofThumbnailKeysByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TodoWorkItem wi
            SET wi.assignee = null, wi.proofImageKey = null, wi.proofThumbnailKey = null
            WHERE wi.assignee.id = :userId
              AND wi.status <> com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
            """)
    int anonymizeFinishedByAssigneeId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM TodoWorkItem wi
            WHERE wi.assignee.id = :userId
              AND wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
              AND wi.type = com.todo.domain.todo.entity.WorkItemType.DIRECT
            """)
    int deleteInProgressDirectByAssigneeId(@Param("userId") Long userId);

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            WHERE wi.assignee.id = :userId
              AND wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
              AND wi.type = :type
            """)
    List<TodoWorkItem> findInProgressByAssigneeIdAndType(
            @Param("userId") Long userId,
            @Param("type") WorkItemType type
    );

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            WHERE wi.todo.team.id = :teamId
              AND wi.assignee.id = :assigneeId
              AND wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
            """)
    List<TodoWorkItem> findInProgressByTeamIdAndAssigneeId(
            @Param("teamId") Long teamId,
            @Param("assigneeId") Long assigneeId
    );

    @Query("""
            SELECT wi FROM TodoWorkItem wi
            WHERE wi.assignee.id = :assigneeId
              AND wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
            """)
    List<TodoWorkItem> findInProgressByAssigneeId(@Param("assigneeId") Long assigneeId);

    @Query("""
            SELECT wi.submittedAt AS occurredAt, wi.assignee.id AS userId, wi.todo.id AS todoId
            FROM TodoWorkItem wi
            WHERE wi.todo.team.id = :teamId
              AND wi.assignee IS NOT NULL
              AND wi.submittedAt >= :from
            """)
    List<UserActivityRecord> findSubmissionActivityByTeamId(
            @Param("teamId") Long teamId,
            @Param("from") LocalDateTime from
    );

    @Query("""
            SELECT wi.submittedAt AS occurredAt, wi.assignee.id AS userId, wi.todo.id AS todoId
            FROM TodoWorkItem wi
            WHERE wi.assignee.id = :userId
              AND wi.submittedAt >= :from
            """)
    List<UserActivityRecord> findSubmissionActivityByAssigneeId(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from
    );
}
