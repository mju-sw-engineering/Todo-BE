package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByInviteCode(String inviteCode);

    Optional<Team> findByInviteCode(String inviteCode);

    Optional<Team> findByInviteLinkToken(String inviteLinkToken);

    @Modifying
    @Query("""
            UPDATE Team t
            SET t.successCount = t.successCount + 1
            WHERE t.id = :teamId
            """)
    int incrementSuccessCount(@Param("teamId") Long teamId);
}
