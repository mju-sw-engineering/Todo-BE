package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TodoParticipantRepository extends JpaRepository<TodoParticipant, Long> {

    @Query("""
            SELECT tp.todo.id AS todoId, tp.user.id AS userId, tp.status AS status
            FROM TodoParticipant tp
            WHERE tp.todo.id IN :todoIds
            """)
    List<TodoParticipantSummary> findSummaryByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Query("""
            SELECT tp.user.id AS userId, tp.user.nickname AS nickname,
                   tp.user.profileImageUrl AS profileImageUrl,
                   tp.proofImageKey AS proofImageKey, tp.status AS status
            FROM TodoParticipant tp
            WHERE tp.todo.id = :todoId
            """)
    List<TodoParticipantDetail> findDetailByTodoId(@Param("todoId") Long todoId);
}
