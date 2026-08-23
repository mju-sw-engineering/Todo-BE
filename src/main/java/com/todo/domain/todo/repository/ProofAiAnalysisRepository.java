package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.ProofAiAnalysis;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface ProofAiAnalysisRepository extends JpaRepository<ProofAiAnalysis, Long> {

    /**
     * 폴러 인스턴스가 여러 개여도 같은 행을 두 번 처리하지 않도록 잠근다.
     * 배치로 id만 먼저 뽑고 건별로 잠그는 것은 한 건이 오래 걸려도 나머지가 막히지 않게 하기 위함이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ProofAiAnalysis a WHERE a.id = :id")
    Optional<ProofAiAnalysis> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT a.id FROM ProofAiAnalysis a
            WHERE a.status = com.todo.domain.todo.entity.ProofAnalysisStatus.PENDING
              AND a.nextAttemptAt <= :now
            ORDER BY a.nextAttemptAt ASC
            """)
    List<Long> findDispatchableIds(@Param("now") LocalDateTime now, Pageable pageable);

    Optional<ProofAiAnalysis> findByWorkItemId(Long workItemId);

    List<ProofAiAnalysis> findByWorkItemIdIn(List<Long> workItemIds);

    /** 카드 목록처럼 여러 WorkItem을 한 번에 그릴 때 N+1을 피하기 위한 조회. */
    default Map<Long, ProofAiAnalysis> findMapByWorkItemIds(List<Long> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return Map.of();
        }
        return findByWorkItemIdIn(workItemIds).stream()
                .collect(Collectors.toMap(a -> a.getWorkItem().getId(), Function.identity()));
    }
}
