package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.repository.TodoWorkItemSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "투두 참가자(담당자) 정보")
public record TodoParticipantResponse(

        @Schema(description = "참가자 ID") Long userId,
        @Schema(description = "참가자 닉네임") String nickname,
        @Schema(description = "참가자 프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "담당 WorkItem을 종합한 상태. 하나라도 진행 중이면 IN_PROGRESS, 아니면 하나라도 실패 시 FAIL, 전부 성공이면 SUCCESS")
        WorkItemStatus status,
        @Schema(description = "담당 WorkItem 중 성공한 수") int successCount,
        @Schema(description = "담당 WorkItem 총 수") int totalCount
) {
    public static TodoParticipantResponse from(
            TodoWorkItemSummary workItem,
            WorkItemStatus status,
            int successCount,
            int totalCount
    ) {
        return new TodoParticipantResponse(
                workItem.getAssigneeId(),
                workItem.getNickname(),
                workItem.getProfileImageUrl(),
                status,
                successCount,
                totalCount
        );
    }
}
