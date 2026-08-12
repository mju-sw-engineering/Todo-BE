package com.todo.domain.user.repository;

import com.todo.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
    Optional<User> findByLoginId(String loginId);
    Optional<User> findBySocialId(String socialId);
    Optional<User> findByEmail(String email);

    /**
     * 같은 계정으로 동시에 로그인/재발급이 들어와도 세션 발급(저장 + 5개 초과분 정리)이
     * 서로 어긋나지 않도록 {@link com.todo.domain.auth.service.SessionService}가 직렬화 용도로 쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
