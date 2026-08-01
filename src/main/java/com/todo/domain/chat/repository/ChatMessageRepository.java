package com.todo.domain.chat.repository;

import com.todo.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender " +
            "WHERE m.todo.id = :todoId " +
            "ORDER BY m.id DESC")
    List<ChatMessage> findLatestMessages(
            @Param("todoId") Long todoId,
            Pageable pageable
    );

    @Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender " +
            "WHERE m.todo.id = :todoId AND m.id < :cursorId " +
            "ORDER BY m.id DESC")
    List<ChatMessage> findMessagesByCursor(
            @Param("todoId") Long todoId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.todo.id IN :todoIds")
    void deleteByTodoIdIn(@Param("todoIds") List<Long> todoIds);

    @Modifying
    @Query("UPDATE ChatMessage c SET c.sender = null WHERE c.sender.id = :userId")
    void clearSenderByUserId(@Param("userId") Long userId);
}
