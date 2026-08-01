package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

    Optional<EmailVerification> findByTokenAndUsedFalse(String token);

    /**
     * 인증 기록은 이메일 주소 자체가 개인정보이므로 탈퇴 시 삭제한다.
     * users를 참조하지 않아 FK 제약은 없지만 개인정보 파기 대상이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EmailVerification e WHERE e.email = :email")
    int deleteByEmail(@Param("email") String email);
}
