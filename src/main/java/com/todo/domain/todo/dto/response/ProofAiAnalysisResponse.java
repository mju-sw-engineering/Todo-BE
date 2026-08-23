package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.ProofAiAnalysis;
import com.todo.domain.todo.entity.ProofAnalysisStatus;
import com.todo.domain.todo.entity.ProofVerdict;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 파일 AI 판정 결과. 판정 대상이 아니거나 아직 판정 전이면 null입니다.")
public record ProofAiAnalysisResponse(

        @Schema(description = "판정 상태. PENDING이면 아직 결과가 없습니다.") ProofAnalysisStatus status,
        @Schema(description = "판정. DONE일 때만 값이 있습니다.") ProofVerdict verdict,
        @Schema(description = "팀에 공개되는 한 줄 요약. 근거가 부족한(UNCERTAIN) 판정은 요약을 공개하지 않습니다.")
        String summary,
        @Schema(description = "불일치 사유. 요청자가 제출자 본인일 때만 내려가며 그 외에는 항상 null입니다.")
        String mismatchReason
) {
    /**
     * @param viewerId 응답을 받을 사용자. 불일치 사유는 제출자 본인에게만 노출된다 — 팀원 화면에
     *                 사유가 보이면 오탐 한 번이 팀 앞에서 팀원을 몰아세우는 일이 된다.
     *                 이 판별을 호출자가 아니라 여기서 하는 이유는, DTO를 만드는 경로가 늘어나도
     *                 노출 조건을 빠뜨릴 수 없게 하기 위해서다.
     */
    public static ProofAiAnalysisResponse from(ProofAiAnalysis analysis, Long viewerId) {
        if (analysis == null || analysis.getStatus() == ProofAnalysisStatus.SKIPPED) {
            return null;
        }
        boolean viewerIsSubmitter = viewerId != null
                && analysis.getWorkItem().getAssignee() != null
                && viewerId.equals(analysis.getWorkItem().getAssignee().getId());
        return new ProofAiAnalysisResponse(
                analysis.getStatus(),
                analysis.getVerdict(),
                analysis.hasTeamVisibleSummary() ? analysis.getSummary() : null,
                viewerIsSubmitter ? analysis.getMismatchReason() : null
        );
    }
}
