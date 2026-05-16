package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    @Query("SELECT t FROM Todo t JOIN FETCH t.creator WHERE t.team.id = :teamId ORDER BY t.createdAt DESC")
    List<Todo> findByTeamIdWithCreator(@Param("teamId") Long teamId);

    @Query("SELECT t FROM Todo t JOIN FETCH t.creator JOIN FETCH t.team WHERE t.id = :todoId")
    Optional<Todo> findByIdWithCreatorAndTeam(@Param("todoId") Long todoId);
}
