package com.todo.global.file.repository;

import com.todo.global.file.entity.UploadLedger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UploadLedgerRepository extends JpaRepository<UploadLedger, Long> {

    /**
     * 유예 시간이 지난 행을 키셋 페이지네이션으로 걷는다. dry-run은 행을 지우지 않으므로
     * 오프셋 대신 마지막으로 본 id를 커서로 써야 같은 행을 무한히 다시 읽지 않는다.
     */
    @Query("""
            SELECT u FROM UploadLedger u
            WHERE u.createdAt < :threshold
              AND u.id > :cursor
            ORDER BY u.id ASC
            """)
    List<UploadLedger> findExpiredAfterCursor(
            @Param("threshold") LocalDateTime threshold,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
