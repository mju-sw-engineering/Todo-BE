package com.todo.domain.todo.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 판정 트랜잭션이 커밋된 뒤에만 팀 채널로 발송한다. 롤백된 판정이 화면에 뜨는 것을 막는다.
 * 알림 쪽 {@code NotificationEventListener}와 같은 구조다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProofAnalysisEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProofAnalysisCompleted(ProofAnalysisCompletedEvent event) {
        try {
            // 채팅과 채널을 공유하지 않는다. 기존 /topic/teams/{id} 구독자는 모든 메시지를
            // 채팅으로 취급하므로, 다른 모양의 페이로드를 흘리면 프론트가 깨진다.
            messagingTemplate.convertAndSend(
                    "/topic/teams/" + event.teamId() + "/proof-analyses",
                    event.payload()
            );
        } catch (Exception e) {
            // 발송이 실패해도 판정은 이미 저장돼 REST 조회로 복구된다. 로깅만 한다.
            log.warn("인증 파일 판정 WebSocket 전송 실패. workItemId={}", event.payload().workItemId(), e);
        }
    }
}
