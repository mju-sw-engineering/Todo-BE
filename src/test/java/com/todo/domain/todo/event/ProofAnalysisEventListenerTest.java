package com.todo.domain.todo.event;

import com.todo.domain.todo.dto.response.ProofAnalysisPushResponse;
import com.todo.domain.todo.entity.ProofAnalysisStatus;
import com.todo.domain.todo.entity.ProofVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ProofAnalysisEventListenerTest {

    @InjectMocks
    private ProofAnalysisEventListener listener;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void 팀_전용_토픽으로_발송한다() {
        // 채팅이 쓰는 /topic/teams/{id}를 공유하면 기존 구독자가 이 페이로드를 채팅으로 해석한다.
        listener.onProofAnalysisCompleted(event(5L));

        then(messagingTemplate).should().convertAndSend("/topic/teams/5/proof-analyses", payload());
    }

    @Test
    void 발송에_실패해도_예외를_밖으로_던지지_않는다() {
        // 커밋 후 리스너라 여기서 터지면 이미 저장된 판정과 무관하게 로그만 지저분해진다.
        // 놓친 갱신은 카드 재진입 시 REST 응답으로 복구된다.
        willThrow(new IllegalStateException("브로커 장애"))
                .given(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        assertThatCode(() -> listener.onProofAnalysisCompleted(event(5L)))
                .doesNotThrowAnyException();
    }

    private ProofAnalysisCompletedEvent event(Long teamId) {
        return new ProofAnalysisCompletedEvent(teamId, payload());
    }

    private ProofAnalysisPushResponse payload() {
        return new ProofAnalysisPushResponse(
                10L, 20L, ProofAnalysisStatus.DONE, ProofVerdict.VERIFIED, "회의록 사진입니다.");
    }
}
