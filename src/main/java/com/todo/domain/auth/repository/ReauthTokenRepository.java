package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.ReauthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReauthTokenRepository extends JpaRepository<ReauthToken, Long> {

    @Query("SELECT t FROM ReauthToken t JOIN FETCH t.user WHERE t.tokenHash = :tokenHash")
    Optional<ReauthToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 미사용 토큰만 사용됨으로 바꾼다. 갱신된 행 수가 1일 때만 소비에 성공한 것이다.
     *
     * <p>비관적 락 대신 조건부 UPDATE를 쓴다. 동시에 들어온 두 요청 중 하나만 1행을 갱신하므로
     * DB가 1회용을 보장하고, 이미 영속성 컨텍스트에 올라온 엔티티를 다시 잠그려다
     * 락 모드 상향에 실패하는 문제도 없다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ReauthToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markAsUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 같은 용도의 미사용 토큰을 정리한다. 새로 발급하면 이전 것은 무효가 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReauthToken t WHERE t.user.id = :userId AND t.purpose = :purpose")
    int deleteByUserIdAndPurpose(
            @Param("userId") Long userId,
            @Param("purpose") com.todo.domain.auth.entity.ReauthPurpose purpose
    );

    /**
     * 탈퇴 시 정리 대상. user_id FK가 RESTRICT라 빠뜨리면 users 삭제가 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReauthToken t WHERE t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ReauthToken t WHERE t.expiresAt < :threshold")
    int deleteByExpiresAtBefore(@Param("threshold") LocalDateTime threshold);
}
