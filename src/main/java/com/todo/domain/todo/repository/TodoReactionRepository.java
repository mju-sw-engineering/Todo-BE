package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoReactionRepository extends JpaRepository<TodoReaction, Long> {

    Optional<TodoReaction> findByTodoWorkItemIdAndUserId(Long todoWorkItemId, Long userId);

    @Query("""
            SELECT tr.todoWorkItem.id AS todoWorkItemId,
                   tr.reactionType AS reactionType,
                   COUNT(tr) AS reactionCount
            FROM TodoReaction tr
            WHERE tr.todoWorkItem.id IN :todoWorkItemIds
            GROUP BY tr.todoWorkItem.id, tr.reactionType
            """)
    List<TodoReactionCount> countByTodoWorkItemIds(@Param("todoWorkItemIds") List<Long> todoWorkItemIds);

    List<TodoReaction> findByTodoWorkItemIdInAndUserId(List<Long> todoWorkItemIds, Long userId);

    long countByTodoWorkItemIdAndReactionType(Long todoWorkItemId, TodoReactionType reactionType);

    @Modifying
    @Query("DELETE FROM TodoReaction tr WHERE tr.todoWorkItem.id IN :workItemIds")
    void deleteByTodoWorkItemIdIn(@Param("workItemIds") List<Long> workItemIds);

    @Modifying
    @Query("DELETE FROM TodoReaction tr WHERE tr.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
