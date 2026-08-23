package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.ProofAiAnalysis;
import com.todo.domain.todo.entity.ProofAnalysisStatus;
import com.todo.domain.todo.entity.ProofVerdict;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 판정이 끝났음을 팀 채널로 알리는 페이로드.
 *
 * <p>전용 토픽({@code /topic/teams/{teamId}/proof-analyses})으로 나간다. 채팅이 쓰는
 * {@code /topic/teams/{teamId}}를 공유하면 기존 구독자가 이 페이로드를 채팅으로 해석한다.
 *
 * <p><b>이 레코드에는 불일치 사유 필드가 없다.</b> 팀 전체에게 브로드캐스트되는 값이라
 * 사유가 실리면 오탐 한 번이 팀 앞에서 팀원을 몰아세우는 일이 된다. 실수로 넣을 수 없도록
 * 필드 자체를 두지 않았다 — 사유가 필요한 제출자 본인은 REST 조회
 * ({@link ProofAiAnalysisResponse})나 개인 알림으로 받는다.
 */
@Schema(description = "인증 파일 판정 완료 알림 (팀 전용 토픽 브로드캐스트)")
public record ProofAnalysisPushResponse(

        @Schema(description = "Todo ID") Long todoId,
        @Schema(description = "WorkItem ID") Long workItemId,
        @Schema(description = "판정 상태") ProofAnalysisStatus status,
        @Schema(description = "판정") ProofVerdict verdict,
        @Schema(description = "팀에 공개되는 요약. 근거가 부족한 판정은 요약을 공개하지 않습니다.")
        String summary
) {
    public static ProofAnalysisPushResponse from(ProofAiAnalysis analysis) {
        return new ProofAnalysisPushResponse(
                analysis.getWorkItem().getTodo().getId(),
                analysis.getWorkItem().getId(),
                analysis.getStatus(),
                analysis.getVerdict(),
                analysis.hasTeamVisibleSummary() ? analysis.getSummary() : null
        );
    }
}
