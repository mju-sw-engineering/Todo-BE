package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("select tm.team from TeamMember tm where tm.user.id = :userId order by tm.joinedAt asc")
    List<Team> findTeamsByUserId(@Param("userId") Long userId);

    @Query("select tm from TeamMember tm join fetch tm.user where tm.team.id = :teamId")
    List<TeamMember> findByTeamIdWithUser(@Param("teamId") Long teamId);

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.user.id != :userId ORDER BY tm.joinedAt ASC")
    List<TeamMember> findByTeamIdExcludingUser(@Param("teamId") Long teamId, @Param("userId") Long userId);

    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.team WHERE tm.user.id = :userId AND tm.role = 'LEADER'")
    List<TeamMember> findLeaderMembershipsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM TeamMember tm WHERE tm.team.id = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);

    @Modifying
    @Query("DELETE FROM TeamMember tm WHERE tm.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
