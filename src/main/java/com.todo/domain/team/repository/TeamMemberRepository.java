package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("select tm.team from TeamMember tm where tm.user.id = :userId order by tm.joinedAt asc")
    List<Team> findTeamsByUserId(@Param("userId") Long userId);
}
