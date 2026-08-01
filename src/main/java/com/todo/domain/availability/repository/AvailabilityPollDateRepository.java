package com.todo.domain.availability.repository;

import com.todo.domain.availability.entity.AvailabilityPollDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityPollDateRepository extends JpaRepository<AvailabilityPollDate, Long> {
}
