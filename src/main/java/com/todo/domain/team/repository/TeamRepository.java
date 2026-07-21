package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByInviteCode(String inviteCode);

    Optional<Team> findByInviteCode(String inviteCode);
}
