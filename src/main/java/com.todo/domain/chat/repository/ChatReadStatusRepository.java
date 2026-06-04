package com.todo.domain.chat.repository;

import com.todo.domain.chat.entity.ChatReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatReadStatusRepository extends JpaRepository<ChatReadStatus, Long> {

    Optional<ChatReadStatus> findByUserIdAndTodoId(Long userId, Long todoId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.todo.id = :todoId AND m.id > :lastReadMessageId")
    long countUnreadMessages(@Param("todoId") Long todoId, @Param("lastReadMessageId") Long lastReadMessageId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.todo.id = :todoId")
    long countAllMessages(@Param("todoId") Long todoId);
}
