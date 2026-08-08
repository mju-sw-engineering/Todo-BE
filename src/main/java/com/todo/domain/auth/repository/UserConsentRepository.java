package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    Optional<UserConsent> findTopByUserIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc(
            Long userId, ConsentType consentType);

    /**
     * 동의 이력은 개인정보이므로 탈퇴 시 보존하지 않고 삭제한다.
     * user_id FK는 RESTRICT이므로 이 정리를 빠뜨리면 users 삭제가 FK 위반으로 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserConsent c WHERE c.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
