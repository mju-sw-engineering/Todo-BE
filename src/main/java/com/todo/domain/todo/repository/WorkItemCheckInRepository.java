package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.WorkItemCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkItemCheckInRepository extends JpaRepository<WorkItemCheckIn, Long> {

    boolean existsByWorkItemIdAndUserIdAndCheckDate(Long workItemId, Long userId, LocalDate checkDate);

    @Query("""
            SELECT c FROM WorkItemCheckIn c
            JOIN FETCH c.user
            WHERE c.workItem.id = :workItemId
            ORDER BY c.checkDate DESC, c.id DESC
            """)
    List<WorkItemCheckIn> findByWorkItemIdWithUser(@Param("workItemId") Long workItemId);

    @Query("""
            SELECT c.checkDate AS occurredOn, c.user.id AS userId, c.workItem.todo.id AS todoId
            FROM WorkItemCheckIn c
            WHERE c.workItem.todo.team.id = :teamId
              AND c.checkDate >= :from
            """)
    List<CheckInActivityRecord> findActivityByTeamId(
            @Param("teamId") Long teamId,
            @Param("from") LocalDate from
    );

    @Query("""
            SELECT c.checkDate AS occurredOn, c.user.id AS userId, c.workItem.todo.id AS todoId
            FROM WorkItemCheckIn c
            WHERE c.user.id = :userId
              AND c.checkDate >= :from
            """)
    List<CheckInActivityRecord> findActivityByUserId(
            @Param("userId") Long userId,
            @Param("from") LocalDate from
    );
}
