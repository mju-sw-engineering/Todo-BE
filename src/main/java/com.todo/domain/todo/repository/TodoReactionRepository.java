package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoReactionRepository extends JpaRepository<TodoReaction, Long> {

    Optional<TodoReaction> findByTodoParticipantIdAndUserId(Long todoParticipantId, Long userId);

    @Query("""
            SELECT tr.todoParticipant.id AS todoParticipantId,
                   tr.reactionType AS reactionType,
                   COUNT(tr) AS reactionCount
            FROM TodoReaction tr
            WHERE tr.todoParticipant.id IN :todoParticipantIds
            GROUP BY tr.todoParticipant.id, tr.reactionType
            """)
    List<TodoReactionCount> countByTodoParticipantIds(@Param("todoParticipantIds") List<Long> todoParticipantIds);

    List<TodoReaction> findByTodoParticipantIdInAndUserId(List<Long> todoParticipantIds, Long userId);

    long countByTodoParticipantIdAndReactionType(Long todoParticipantId, TodoReactionType reactionType);
}
