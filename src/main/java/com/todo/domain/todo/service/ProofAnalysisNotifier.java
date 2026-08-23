package com.todo.domain.todo.service;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.todo.dto.response.ProofAnalysisPushResponse;
import com.todo.domain.todo.event.ProofAnalysisCompletedEvent;
import com.todo.domain.todo.entity.ProofAiAnalysis;
import com.todo.domain.todo.entity.ProofVerdict;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 판정 결과에 따른 후속 통지.
 *
 * <p>이 클래스의 핵심은 <b>무엇을 보내느냐가 아니라 누구에게 보내지 않느냐</b>다.
 * 불일치 판정은 제출자 본인에게만 간다. 팀에 알리면 오탐 한 번이 팀원을 공개적으로
 * 몰아세우는 일이 되고, 그건 이 기능이 막으려던 것보다 큰 피해다.
 *
 * <p>저장은 호출자 트랜잭션에 포함되고 실제 WebSocket 발송은 커밋 후로 미뤄진다
 * ({@link NotificationService}가 처리). 롤백된 판정이 사용자에게 노출되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProofAnalysisNotifier {

    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 팀 채널로 판정 결과를 알려 카드가 새로고침 없이 갱신되게 한다.
     *
     * <p>verdict와 무관하게 보낸다. MISMATCH도 팀에서는 뱃지가 붙지 않는다는 사실 자체가
     * 정보이고, 사유는 페이로드에 아예 없어 새어나갈 수 없다.
     *
     * <p>발송은 best-effort다. 놓쳐도 카드 재진입 시 REST 응답으로 같은 값이 내려온다.
     */
    private void publishToTeam(ProofAiAnalysis analysis) {
        eventPublisher.publishEvent(new ProofAnalysisCompletedEvent(
                analysis.getWorkItem().getTodo().getTeam().getId(),
                ProofAnalysisPushResponse.from(analysis)
        ));
    }

    public void afterAnalyzed(ProofAiAnalysis analysis) {
        publishToTeam(analysis);

        if (analysis.getVerdict() != ProofVerdict.MISMATCH) {
            // VERIFIED는 뱃지로만 드러나고 알림을 보내지 않는다. 정상 제출마다 알림이 오면
            // 소음이 된다. UNCERTAIN은 근거가 부족한 건이라 아무 신호도 주지 않는다.
            return;
        }

        TodoWorkItem workItem = analysis.getWorkItem();
        User submitter = workItem.getAssignee();
        if (submitter == null) {
            // 탈퇴로 익명화된 제출. 알릴 대상이 없다.
            return;
        }

        Todo todo = workItem.getTodo();
        notificationService.send(
                submitter,
                null,
                notificationMessageFactory.aiProofMismatch(todo.getTitle()),
                workItem.getId(),
                todo.getTeam().getId()
        );
        log.info("인증 파일 불일치를 제출자에게 안내했습니다. workItemId={}", workItem.getId());
    }
}
