package com.todo.global.mail.repository;

import com.todo.global.mail.entity.MailOutbox;
import com.todo.global.mail.entity.MailOutboxStatus;
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

public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MailOutbox m WHERE m.id = :id")
    Optional<MailOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT m.id FROM MailOutbox m
            WHERE m.status = com.todo.global.mail.entity.MailOutboxStatus.PENDING
              AND m.nextAttemptAt <= :now
            ORDER BY m.nextAttemptAt ASC
            """)
    List<Long> findDispatchableIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM MailOutbox m
            WHERE m.status IN :statuses
              AND m.updatedAt < :threshold
            """)
    int deleteByStatusInAndUpdatedAtBefore(
            @Param("statuses") List<MailOutboxStatus> statuses,
            @Param("threshold") LocalDateTime threshold
    );
}
