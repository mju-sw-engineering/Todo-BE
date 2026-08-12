package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.user WHERE t.token = :token")
    Optional<RefreshToken> findByToken(@Param("token") String token);

    /**
     * 세션 목록 조회와 5개 초과분 정리(evict)에 쓰는 활성 세션 목록. 최신순으로 정렬한다.
     */
    @Query("SELECT t FROM RefreshToken t WHERE t.user.id = :userId AND t.isUsed = false AND t.expiresAt > :now "
            + "ORDER BY t.createdAt DESC")
    List<RefreshToken> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 본인 세션만 지울 수 있도록 소유자를 함께 확인한다. 갱신된 행 수가 0이면 대상이 없거나
     * 남의 세션이라는 뜻이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.id = :id AND t.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId AND t.isUsed = false")
    int deleteActiveByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
