package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoVoteRepository extends JpaRepository<TodoVote, Long> {
}
