package com.todo.domain.todo.event;

import com.todo.domain.todo.dto.response.ProofAnalysisPushResponse;

/**
 * 판정이 확정됐음을 알리는 이벤트. 커밋 후 WebSocket 발송을 트리거하는 데 쓴다.
 *
 * <p>엔티티가 아니라 발송에 필요한 값만 담는다. 커밋 후에는 영속성 컨텍스트가 닫혀 있을 수
 * 있어 지연 로딩이 터지기 때문이다. 알림 쪽 {@code NotificationDelivery}와 같은 이유다.
 */
public record ProofAnalysisCompletedEvent(Long teamId, ProofAnalysisPushResponse payload) {
}
