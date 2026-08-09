package com.todo.domain.auth.event;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppleAccountRevokeRequested(AppleAccountRevokeRequestedEvent event) {
        try {
            appleTokenClient.revokeRefreshToken(event.appleRefreshToken(), event.appleClientId());
        } catch (Exception e) {
            log.warn("Apple revoke 실패, 탈퇴는 이미 완료된 상태라 재시도 없이 넘어감: userId={}", event.userId(), e);
        }
    }
}
