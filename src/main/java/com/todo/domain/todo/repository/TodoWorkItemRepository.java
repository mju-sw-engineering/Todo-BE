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

    /**
     * 마감이 지나 곧 FAIL 처리될 작업의 담당자 정보를 벌크 업데이트 전에 미리 읽어둔다.
     * {@link #markOverdueAsFail}이 {@code clearAutomatically}로 영속성 컨텍스트를 비우므로,
     * 업데이트 뒤에는 이 조회 결과로 받은 값만 쓰고 엔티티 참조는 다시 조회해서 써야 한다.
     */
    @Query("""
            SELECT wi.id AS workItemId, wi.todo.id AS todoId, wi.todo.title AS todoTitle,
                   wi.assignee.id AS assigneeId, wi.todo.team.id AS teamId
            FROM TodoWorkItem wi
            WHERE wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
              AND wi.assignee IS NOT NULL
              AND COALESCE(wi.deadline, wi.todo.deadline) < :now
            """)
    List<TodoWorkItemNotificationInfo> findOverdueForNotification(@Param("now") LocalDateTime now);

    /**
     * 마감이 지난 진행 중 작업을 실패로 바꾼다.
     *
     * WHERE에서 {@code wi.todo.deadline}처럼 암시적 조인을 쓰면 Hibernate가
     * MySQL의 {@code UPDATE ... JOIN}으로 변환하면서 SET의 status 컬럼을 테이블 한정 없이
     * 생성한다. 두 테이블 모두 status 컬럼이 있어 "Column 'status' is ambiguous"로
     * 매 실행이 실패하므로, 조인이 생기지 않게 상관 서브쿼리로 투두 마감을 읽는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TodoWorkItem wi
            SET wi.status = com.todo.domain.todo.entity.WorkItemStatus.FAIL
            WHERE wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
              AND COALESCE(wi.deadline,
                    (SELECT t.deadline FROM Todo t WHERE t.id = wi.todo.id)) < :now
            """)
    int markOverdueAsFail(@Param("now") LocalDateTime now);

    /**
     * 마감이 {@code windowEnd} 이내로 다가왔고 아직 리마인더를 보내지 않은 작업.
     * 아직 리마인더를 안 보냈다는 조건이 없으면 스케줄러가 tick마다 같은 작업을 중복 발송한다.
     */
    @Query("""
            SELECT wi.id AS workItemId, wi.todo.id AS todoId, wi.todo.title AS todoTitle,
                   wi.assignee.id AS assigneeId, wi.todo.team.id AS teamId
            FROM TodoWorkItem wi
            WHERE wi.status = com.todo.domain.todo.entity.WorkItemStatus.IN_PROGRESS
              AND wi.assignee IS NOT NULL
              AND wi.deadlineReminderSentAt IS NULL
              AND COALESCE(wi.deadline, wi.todo.deadline) >= :now
              AND COALESCE(wi.deadline, wi.todo.deadline) < :windowEnd
            """)
    List<TodoWorkItemNotificationInfo> findApproachingDeadlineWorkItems(
            @Param("now") LocalDateTime now,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TodoWorkItem wi SET wi.deadlineReminderSentAt = :sentAt WHERE wi.id IN :workItemIds")
    int markDeadlineReminderSent(@Param("workItemIds") List<Long> workItemIds, @Param("sentAt") LocalDateTime sentAt);

    @Query("""
            SELECT wi.todo.id AS todoId, a.id AS assigneeId,
                   a.nickname AS nickname, a.profileImageUrl AS profileImageUrl,
                   wi.status AS status, wi.type AS type, wi.position AS position
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

    /** /팀현황 명령어용 — 진행 중인 투두에 속한 WorkItem만 상태별로 집계한다. */
    @Query("""
            SELECT wi.status AS status, COUNT(wi) AS count
            FROM TodoWorkItem wi
            WHERE wi.todo.team.id = :teamId
              AND wi.todo.status = com.todo.domain.todo.entity.TodoStatus.IN_PROGRESS
            GROUP BY wi.status
            """)
    List<WorkItemStatusCount> countByTeamIdAndTodoInProgressGroupByStatus(@Param("teamId") Long teamId);
}
