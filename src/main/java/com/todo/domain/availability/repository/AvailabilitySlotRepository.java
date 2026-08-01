package com.todo.domain.availability.repository;

import com.todo.domain.availability.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    @Query("SELECT s FROM AvailabilitySlot s JOIN FETCH s.user WHERE s.poll.id = :pollId AND s.user IS NOT NULL")
    List<AvailabilitySlot> findByPollIdWithUser(@Param("pollId") Long pollId);

    List<AvailabilitySlot> findByPollIdAndUserId(Long pollId, Long userId);

    @Query("SELECT COUNT(DISTINCT s.user.id) FROM AvailabilitySlot s WHERE s.poll.id = :pollId AND s.user IS NOT NULL")
    long countRespondedUsers(@Param("pollId") Long pollId);

    @Query("SELECT s.poll.id, COUNT(DISTINCT s.user.id) FROM AvailabilitySlot s WHERE s.poll.id IN :pollIds AND s.user IS NOT NULL GROUP BY s.poll.id")
    List<Object[]> countRespondedUsersByPollIds(@Param("pollIds") List<Long> pollIds);

    @Query("SELECT DISTINCT s.poll.id FROM AvailabilitySlot s WHERE s.poll.id IN :pollIds AND s.user.id = :userId")
    List<Long> findPollIdsRespondedByUser(@Param("pollIds") List<Long> pollIds, @Param("userId") Long userId);

    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    @Modifying
    @Query("DELETE FROM AvailabilitySlot s WHERE s.poll.id = :pollId AND s.user.id = :userId")
    void deleteByPollIdAndUserId(@Param("pollId") Long pollId, @Param("userId") Long userId);
}
