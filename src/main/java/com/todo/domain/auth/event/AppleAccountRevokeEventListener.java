package com.todo.domain.auth.event;

import com.todo.domain.auth.service.AppleRevokeOutboxService;
import com.todo.domain.auth.service.apple.AppleTokenClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppleAccountRevokeEventListener {

    private final AppleTokenClient appleTokenClient;
    private final AppleRevokeOutboxService appleRevokeOutboxService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppleAccountRevokeRequested(AppleAccountRevokeRequestedEvent event) {
        try {
            appleTokenClient.revokeRefreshToken(event.appleRefreshToken(), event.appleClientId());
        } catch (Exception e) {
            log.warn("Apple revoke 첫 시도 실패, 재시도를 위해 outbox에 적재함: userId={}", event.userId(), e);
            appleRevokeOutboxService.enqueue(event.userId(), event.appleRefreshToken(), event.appleClientId());
        }
    }
}
