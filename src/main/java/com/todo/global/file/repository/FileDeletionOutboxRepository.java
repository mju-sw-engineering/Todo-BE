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
import java.util.Collection;
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

    /**
     * 고아 파일 정리가 이미 outbox에 올라간 키를 걸러낼 때 쓴다. 상태를 가리지 않는다 —
     * PENDING이면 폴러가 지울 예정이라 이중 처리이고, DELETED면 이미 지워진 키다.
     * 어느 쪽이든 정리 스케줄러가 손댈 일이 아니다.
     */
    @Query("SELECT f.objectKey FROM FileDeletionOutbox f WHERE f.objectKey IN :keys")
    List<String> findObjectKeysIn(@Param("keys") Collection<String> keys);
}
