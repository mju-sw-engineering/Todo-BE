package com.todo.domain.chat.command.repository;

import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 결과를 고쳐 쓰기 위한 잠금 조회. 추천 카드의 [등록]은 읽고-고치고-쓰기라, 두 팀원이 동시에
     * 누르면 잠금 없이는 같은 투두가 두 개 생긴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM SlashCommandExecution e WHERE e.chatMessage.id = :chatMessageId AND e.team.id = :teamId")
    Optional<SlashCommandExecution> findByChatMessageIdAndTeamIdForUpdate(
            @Param("chatMessageId") Long chatMessageId,
            @Param("teamId") Long teamId
    );

    /** 쿨다운 판정용. 같은 명령어의 최근 실행 한 건을 본다. */
    Optional<SlashCommandExecution> findFirstByTeamIdAndCommandOrderByIdDesc(Long teamId, SlashCommand command);

    @Modifying
    @Query("UPDATE SlashCommandExecution e SET e.executor = null WHERE e.executor.id = :userId")
    void clearExecutorByUserId(@Param("userId") Long userId);
}
