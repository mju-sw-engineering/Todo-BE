package com.todo.domain.auth.service;

import com.todo.domain.auth.entity.AppleRevokeOutbox;
import com.todo.domain.auth.entity.AppleRevokeOutboxStatus;
import com.todo.domain.auth.repository.AppleRevokeOutboxRepository;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleRevokeOutboxService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AppleRevokeOutboxRepository appleRevokeOutboxRepository;
    private final AppleTokenClient appleTokenClient;

    /**
     * 커밋 후 시도한 revoke가 실패했을 때만 호출된다. 이 저장 자체가 실패해도 이미 커밋된
     * 탈퇴를 되돌릴 방법은 없으므로, 실패는 로그로만 남긴다.
     */
    @Transactional
    public void enqueue(Long userId, String appleRefreshToken, String appleClientId) {
        appleRevokeOutboxRepository.save(
                AppleRevokeOutbox.create(userId, appleRefreshToken, appleClientId, LocalDateTime.now(KST)));
    }

    /**
     * 단건 재시도를 시도한다. 비관적 락으로 스케줄러의 중복 실행을 막는다.
     */
    @Transactional
    public void dispatch(Long outboxId) {
        AppleRevokeOutbox outbox = appleRevokeOutboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !outbox.isPending()) {
            return;
        }

        try {
            appleTokenClient.revokeRefreshToken(outbox.getAppleRefreshToken(), outbox.getAppleClientId());
            outbox.markRevoked();
        } catch (RuntimeException e) {
            outbox.recordFailure(LocalDateTime.now(KST));
            if (outbox.getStatus() == AppleRevokeOutboxStatus.FAILED) {
                log.error(
                        "Apple revoke 재시도 최대 횟수 초과, 더 이상 재시도하지 않음. outboxId={}, userId={}",
                        outbox.getId(),
                        outbox.getUserId(),
                        e
                );
            } else {
                log.warn(
                        "Apple revoke 재시도 실패. outboxId={}, attemptCount={}",
                        outbox.getId(),
                        outbox.getAttemptCount(),
                        e
                );
            }
        }
    }
}
