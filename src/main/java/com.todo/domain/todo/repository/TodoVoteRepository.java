package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoVoteRepository extends JpaRepository<TodoVote, Long> {

    @Query("SELECT COUNT(tv) > 0 FROM TodoVote tv WHERE tv.todoParticipant.id = :participantId AND tv.voter.id = :voterId")
    boolean existsByTodoParticipantIdAndVoterId(@Param("participantId") Long participantId, @Param("voterId") Long voterId);
}
