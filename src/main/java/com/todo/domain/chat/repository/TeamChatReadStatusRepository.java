package com.todo.domain.chat.repository;

import com.todo.domain.chat.entity.TeamChatReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamChatReadStatusRepository extends JpaRepository<TeamChatReadStatus, Long> {

    Optional<TeamChatReadStatus> findByUserIdAndTeamId(Long userId, Long teamId);

    @Query("SELECT COUNT(m) FROM TeamChatMessage m WHERE m.team.id = :teamId AND m.id > :lastReadMessageId")
    long countUnreadMessages(@Param("teamId") Long teamId, @Param("lastReadMessageId") Long lastReadMessageId);

    @Query("SELECT COUNT(m) FROM TeamChatMessage m WHERE m.team.id = :teamId")
    long countAllMessages(@Param("teamId") Long teamId);

    @Modifying
    @Query("DELETE FROM TeamChatReadStatus s WHERE s.team.id = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);

    @Modifying
    @Query("DELETE FROM TeamChatReadStatus s WHERE s.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
