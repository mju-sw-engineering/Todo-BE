package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    Optional<UserConsent> findTopByUserLoginIdAndConsentTypeAndRevokedAtIsNullOrderByCreatedAtDesc(
            String loginId, ConsentType consentType);
}
