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

    @Modifying
    @Query("DELETE FROM TeamChatMessage m WHERE m.createdAt < :cutoff")
    void deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
