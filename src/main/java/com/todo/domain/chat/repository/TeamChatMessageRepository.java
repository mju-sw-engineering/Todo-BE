package com.todo.domain.chat.repository;

import com.todo.domain.chat.entity.TeamChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TeamChatMessageRepository extends JpaRepository<TeamChatMessage, Long> {

    @Query("SELECT m FROM TeamChatMessage m LEFT JOIN FETCH m.sender " +
            "WHERE m.team.id = :teamId " +
            "ORDER BY m.id DESC")
    List<TeamChatMessage> findLatestMessages(
            @Param("teamId") Long teamId,
            Pageable pageable
    );

    @Query("SELECT m FROM TeamChatMessage m LEFT JOIN FETCH m.sender " +
            "WHERE m.team.id = :teamId AND m.id < :cursorId " +
            "ORDER BY m.id DESC")
    List<TeamChatMessage> findMessagesByCursor(
            @Param("teamId") Long teamId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM TeamChatMessage m WHERE m.team.id = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);

    @Modifying
    @Query("UPDATE TeamChatMessage m SET m.sender = null WHERE m.sender.id = :userId")
    void clearSenderByUserId(@Param("userId") Long userId);

    /**
     * 보관 기간이 지난 메시지를 배치 단위로 삭제하고 삭제 건수를 반환한다.
     * 한 트랜잭션에서 전부 지우면 삭제 대상 전체에 락이 걸린 채 트랜잭션이 길어지므로
     * LIMIT으로 쪼개고 호출자가 반복한다. JPQL은 DELETE에 LIMIT을 지원하지 않아 네이티브 쿼리를 쓴다.
     */
    @Modifying
    @Query(value = "DELETE FROM team_chat_messages WHERE created_at < :cutoff LIMIT :limit",
            nativeQuery = true)
    int deleteBatchCreatedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
