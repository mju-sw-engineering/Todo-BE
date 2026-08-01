package com.todo.global.file.repository;

import com.todo.global.file.entity.FileDeletionOutbox;
import com.todo.global.file.entity.FileDeletionOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileDeletionOutboxRepository extends JpaRepository<FileDeletionOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FileDeletionOutbox f WHERE f.id = :id")
    Optional<FileDeletionOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT f.id FROM FileDeletionOutbox f
            WHERE f.status = com.todo.global.file.entity.FileDeletionOutboxStatus.PENDING
              AND f.nextAttemptAt <= :now
            ORDER BY f.nextAttemptAt ASC
            """)
    List<Long> findDispatchableIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM FileDeletionOutbox f
            WHERE f.status IN :statuses
              AND f.updatedAt < :threshold
            """)
    int deleteByStatusInAndUpdatedAtBefore(
            @Param("statuses") List<FileDeletionOutboxStatus> statuses,
            @Param("threshold") LocalDateTime threshold
    );
}
