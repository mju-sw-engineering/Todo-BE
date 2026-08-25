package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    /** 고아 파일 정리가 후보 키 중 팀 이미지로 살아있는 키를 걸러낼 때 쓴다. */
    @Query("SELECT t.teamImage FROM Team t WHERE t.teamImage IN :keys")
    List<String> findTeamImageKeysIn(@Param("keys") Collection<String> keys);
}
