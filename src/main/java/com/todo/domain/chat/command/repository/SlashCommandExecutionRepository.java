package com.todo.domain.chat.command.repository;

import com.todo.domain.chat.command.entity.SlashCommandExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SlashCommandExecutionRepository extends JpaRepository<SlashCommandExecution, Long> {

    /**
     * 팀 ID를 함께 조건에 건다. messageId만으로 찾으면 팀원 검증을 통과한 요청자가 다른 팀의
     * messageId를 넣어 그 팀의 TEAM 스코프 결과를 읽을 수 있다.
     */
    Optional<SlashCommandExecution> findByChatMessageIdAndTeamId(Long chatMessageId, Long teamId);

    @Modifying
    @Query("UPDATE SlashCommandExecution e SET e.executor = null WHERE e.executor.id = :userId")
    void clearExecutorByUserId(@Param("userId") Long userId);
}
