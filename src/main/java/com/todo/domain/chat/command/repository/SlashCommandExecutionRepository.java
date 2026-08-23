package com.todo.domain.chat.command.repository;

import com.todo.domain.chat.command.entity.SlashCommandExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SlashCommandExecutionRepository extends JpaRepository<SlashCommandExecution, Long> {

    Optional<SlashCommandExecution> findByChatMessageId(Long chatMessageId);

    @Modifying
    @Query("UPDATE SlashCommandExecution e SET e.executor = null WHERE e.executor.id = :userId")
    void clearExecutorByUserId(@Param("userId") Long userId);
}
