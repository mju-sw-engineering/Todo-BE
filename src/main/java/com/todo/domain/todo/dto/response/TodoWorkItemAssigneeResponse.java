package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Todo WorkItem 재배정 응답")
public record TodoWorkItemAssigneeResponse(

        @Schema(description = "WorkItem ID") Long workItemId,
        @Schema(description = "새 담당자 ID") Long assigneeId,
        @Schema(description = "새 담당자 닉네임") String assigneeNickname,
        @Schema(description = "WorkItem 상태") WorkItemStatus status
) {
    public static TodoWorkItemAssigneeResponse from(TodoWorkItem workItem) {
        return new TodoWorkItemAssigneeResponse(
                workItem.getId(),
                workItem.getAssignee() == null ? null : workItem.getAssignee().getId(),
                workItem.getAssignee() == null ? null : workItem.getAssignee().getNickname(),
                workItem.getStatus()
        );
    }
}
