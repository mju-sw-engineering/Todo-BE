package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.ProofKind;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.user.support.WithdrawnUserDisplay;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;

@Schema(description = "DIRECT Todo 담당자의 WorkItem 현황")
public record TodoDirectAssigneeResponse(

        @Schema(description = "WorkItem ID") Long workItemId,
        @Schema(description = "담당자 ID") Long assigneeId,
        @Schema(description = "담당자 닉네임") String assigneeNickname,
        @Schema(description = "WorkItem 상태") WorkItemStatus status,
        @Schema(description = "제출 시각") OffsetDateTime submittedAt,
        @Schema(description = "인증 사진 썸네일 URL. 문서 제출이면 null입니다.") String thumbnailUrl,
        @Schema(description = "인증 파일 종류. 미제출이거나 메타데이터 이전 제출이면 null입니다.") ProofKind proofKind,
        @Schema(description = "인증 파일 원본 파일명") String proofFileName,
        @Schema(description = "이모지 반응 수") Map<TodoReactionType, Long> reactions,
        @Schema(description = "내 이모지 반응") TodoReactionType myReaction,
        @Schema(description = "진행 중 미배정 여부") boolean unassigned
) {
    public static TodoDirectAssigneeResponse from(
            TodoWorkItem workItem,
            OffsetDateTime submittedAt,
            String thumbnailUrl,
            Map<TodoReactionType, Long> reactions,
            TodoReactionType myReaction
    ) {
        boolean unassigned = workItem.getAssignee() == null && workItem.getStatus() == WorkItemStatus.IN_PROGRESS;
        return new TodoDirectAssigneeResponse(
                workItem.getId(),
                workItem.getAssignee() == null ? null : workItem.getAssignee().getId(),
                unassigned ? null : WithdrawnUserDisplay.nicknameOrWithdrawn(
                        workItem.getAssignee() == null ? null : workItem.getAssignee().getNickname()
                ),
                workItem.getStatus(),
                submittedAt,
                thumbnailUrl,
                workItem.getProofKind(),
                workItem.getProofFileName(),
                reactions,
                myReaction,
                unassigned
        );
    }
}
