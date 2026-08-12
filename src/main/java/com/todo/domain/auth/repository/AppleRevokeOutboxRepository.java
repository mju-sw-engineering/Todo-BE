package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.AppleRevokeOutbox;
import com.todo.domain.auth.entity.AppleRevokeOutboxStatus;
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

public interface AppleRevokeOutboxRepository extends JpaRepository<AppleRevokeOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AppleRevokeOutbox a WHERE a.id = :id")
    Optional<AppleRevokeOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT a.id FROM AppleRevokeOutbox a
            WHERE a.status = com.todo.domain.auth.entity.AppleRevokeOutboxStatus.PENDING
              AND a.nextAttemptAt <= :now
            ORDER BY a.nextAttemptAt ASC
            """)
    List<Long> findDispatchableIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM AppleRevokeOutbox a
            WHERE a.status IN :statuses
              AND a.updatedAt < :threshold
            """)
    int deleteByStatusInAndUpdatedAtBefore(
            @Param("statuses") List<AppleRevokeOutboxStatus> statuses,
            @Param("threshold") LocalDateTime threshold
    );
}
