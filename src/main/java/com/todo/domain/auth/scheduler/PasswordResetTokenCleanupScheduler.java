package com.todo.domain.auth.scheduler;

import com.todo.domain.auth.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 만료된 비밀번호 재설정 토큰을 정리한다. {@link ReauthTokenCleanupScheduler}와 동일한 이유
 * (만료되면 검증을 통과할 수 없으므로 남겨둘 이유가 없고, 무한히 쌓이는 것을 막는다).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetTokenCleanupScheduler {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    @Transactional
    @Scheduled(cron = "${auth.password-reset.cleanup-cron:0 35 3 * * *}", zone = "Asia/Seoul")
    public void cleanupExpiredTokens() {
        int deleted = passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(clock));

        if (deleted > 0) {
            log.info("만료된 비밀번호 재설정 토큰 정리 완료. deletedCount={}", deleted);
        }
    }
}
