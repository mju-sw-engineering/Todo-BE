package com.todo.global.websocket;

import com.todo.domain.team.event.TeamMembershipRevokedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WebSocketSessionRevokeListener {

    private final WebSocketSessionRegistry sessionRegistry;

    // 커밋 후에만 끊는다 — 강퇴/탈퇴 트랜잭션이 롤백되면 세션이 유지되는 게 올바르다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamMembershipRevoked(TeamMembershipRevokedEvent event) {
        sessionRegistry.closeAllForUser(event.userId());
    }
}
