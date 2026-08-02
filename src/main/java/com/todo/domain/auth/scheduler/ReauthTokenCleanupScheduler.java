package com.todo.domain.auth.scheduler;

import com.todo.domain.auth.repository.ReauthTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 만료된 재인증 토큰을 정리한다.
 *
 * <p>사용 여부와 무관하게 만료 시각이 지나면 검증을 통과할 수 없으므로 남겨둘 이유가 없다.
 * TTL이 5분이라 양이 많지 않지만, 쓰고 남은 행이 무한히 쌓이는 것을 막는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReauthTokenCleanupScheduler {

    private final ReauthTokenRepository reauthTokenRepository;
    private final Clock clock;

    @Transactional
    @Scheduled(cron = "${auth.reauth.cleanup-cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void cleanupExpiredTokens() {
        int deleted = reauthTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(clock));

        if (deleted > 0) {
            log.info("만료된 재인증 토큰 정리 완료. deletedCount={}", deleted);
        }
    }
}
