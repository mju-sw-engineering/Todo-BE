package com.todo.domain.availability.repository;

import com.todo.domain.availability.entity.AvailabilityPoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AvailabilityPollRepository extends JpaRepository<AvailabilityPoll, Long> {

    @Query("SELECT p FROM AvailabilityPoll p LEFT JOIN FETCH p.dates WHERE p.team.id = :teamId ORDER BY p.createdAt DESC")
    List<AvailabilityPoll> findByTeamIdWithDates(@Param("teamId") Long teamId);

    @Query("SELECT p FROM AvailabilityPoll p LEFT JOIN FETCH p.dates WHERE p.id = :pollId")
    Optional<AvailabilityPoll> findByIdWithDates(@Param("pollId") Long pollId);
}
