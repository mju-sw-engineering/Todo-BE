package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoWorkItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Todo WorkItem 제출 사진 URL 응답")
public record TodoWorkItemSubmissionResponse(

        @Schema(description = "WorkItem ID") Long workItemId,
        @Schema(description = "제출자 ID") Long assigneeId,
        @Schema(description = "제출 시각") OffsetDateTime submittedAt,
        @Schema(description = "원본 이미지 Presigned URL") String originalUrl,
        @Schema(description = "썸네일 이미지 Presigned URL") String thumbnailUrl,
        @Schema(description = "URL 만료 시각") OffsetDateTime expiresAt
) {
    public static TodoWorkItemSubmissionResponse from(
            TodoWorkItem workItem,
            OffsetDateTime submittedAt,
            String originalUrl,
            String thumbnailUrl,
            OffsetDateTime expiresAt
    ) {
        return new TodoWorkItemSubmissionResponse(
                workItem.getId(),
                workItem.getAssignee() == null ? null : workItem.getAssignee().getId(),
                submittedAt,
                originalUrl,
                thumbnailUrl,
                expiresAt
        );
    }
}
