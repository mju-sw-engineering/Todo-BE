package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.TodoParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoParticipantRepository extends JpaRepository<TodoParticipant, Long> {
}
