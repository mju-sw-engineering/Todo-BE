package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.user.support.WithdrawnUserDisplay;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;

@Schema(description = "TASK Todo의 개별 WorkItem 현황")
public record TodoTaskResponse(

        @Schema(description = "WorkItem ID") Long workItemId,
        @Schema(description = "작업 제목") String title,
        @Schema(description = "작업 설명") String description,
        @Schema(description = "담당자 ID") Long assigneeId,
        @Schema(description = "담당자 닉네임") String assigneeNickname,
        @Schema(description = "개별 마감 시간") OffsetDateTime deadline,
        @Schema(description = "요청 배열 기준 정렬 순서") int position,
        @Schema(description = "WorkItem 상태") WorkItemStatus status,
        @Schema(description = "제출 시각") OffsetDateTime submittedAt,
        @Schema(description = "인증 사진 썸네일 URL") String thumbnailUrl,
        @Schema(description = "이모지 반응 수") Map<TodoReactionType, Long> reactions,
        @Schema(description = "내 이모지 반응") TodoReactionType myReaction,
        @Schema(description = "진행 중 미배정 여부") boolean unassigned
) {
    public static TodoTaskResponse from(
            TodoWorkItem workItem,
            OffsetDateTime deadline,
            OffsetDateTime submittedAt,
            String thumbnailUrl,
            Map<TodoReactionType, Long> reactions,
            TodoReactionType myReaction
    ) {
        boolean unassigned = workItem.getAssignee() == null && workItem.getStatus() == WorkItemStatus.IN_PROGRESS;
        return new TodoTaskResponse(
                workItem.getId(),
                workItem.getTaskTitle(),
                workItem.getTaskDescription(),
                workItem.getAssignee() == null ? null : workItem.getAssignee().getId(),
                unassigned ? null : WithdrawnUserDisplay.nicknameOrWithdrawn(
                        workItem.getAssignee() == null ? null : workItem.getAssignee().getNickname()
                ),
                deadline,
                workItem.getPosition(),
                workItem.getStatus(),
                submittedAt,
                thumbnailUrl,
                reactions,
                myReaction,
                unassigned
        );
    }
}
