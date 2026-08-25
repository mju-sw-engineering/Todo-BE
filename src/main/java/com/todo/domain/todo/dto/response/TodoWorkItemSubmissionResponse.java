package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.ProofKind;
import com.todo.domain.todo.entity.TodoWorkItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Todo WorkItem 제출 파일 URL 응답")
public record TodoWorkItemSubmissionResponse(

        @Schema(description = "WorkItem ID") Long workItemId,
        @Schema(description = "제출자 ID") Long assigneeId,
        @Schema(description = "제출 시각") OffsetDateTime submittedAt,
        @Schema(description = "마감 전 재제출로 파일이 갱신된 적이 있는지") boolean resubmitted,
        @Schema(
                description = "파일 종류. 메타데이터를 저장하기 전에 제출된 건은 null이며, "
                        + "이 경우 종류를 단정할 수 없으므로 다운로드 카드로 표시하세요."
        )
        ProofKind kind,
        @Schema(description = "원본 파일명", example = "발표자료_초안.pdf") String fileName,
        @Schema(description = "파일 MIME 타입", example = "application/pdf") String contentType,
        @Schema(description = "원본 파일 Presigned URL (미리보기·다운로드 공용)") String originalUrl,
        @Schema(description = "썸네일 Presigned URL. 이미지 제출에만 존재합니다.") String thumbnailUrl,
        @Schema(description = "URL 만료 시각") OffsetDateTime expiresAt,
        @Schema(description = "AI 판정 결과. 판정 대상이 아니면 null입니다.") ProofAiAnalysisResponse aiAnalysis
) {
    public static TodoWorkItemSubmissionResponse from(
            TodoWorkItem workItem,
            OffsetDateTime submittedAt,
            String originalUrl,
            String thumbnailUrl,
            OffsetDateTime expiresAt,
            ProofAiAnalysisResponse aiAnalysis
    ) {
        return new TodoWorkItemSubmissionResponse(
                workItem.getId(),
                workItem.getAssignee() == null ? null : workItem.getAssignee().getId(),
                submittedAt,
                workItem.isResubmitted(),
                workItem.getProofKind(),
                workItem.getProofFileName(),
                workItem.getProofContentType(),
                originalUrl,
                thumbnailUrl,
                expiresAt,
                aiAnalysis
        );
    }
}
