package com.todo.domain.auth.repository;

import com.todo.domain.auth.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
}
