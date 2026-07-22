package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

    Optional<EmailVerification> findByTokenAndUsedFalse(String token);
}
