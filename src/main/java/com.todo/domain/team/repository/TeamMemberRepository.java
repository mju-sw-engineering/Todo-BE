package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
