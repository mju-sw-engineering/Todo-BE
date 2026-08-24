package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.repository.TodoWorkItemSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "투두 참가자(담당자) 정보")
public record TodoParticipantResponse(

        @Schema(description = "참가자 ID") Long userId,
        @Schema(description = "참가자 닉네임") String nickname,
        @Schema(description = "참가자 프로필 이미지 URL") String profileImageUrl
) {
    public static TodoParticipantResponse from(TodoWorkItemSummary workItem) {
        return new TodoParticipantResponse(
                workItem.getAssigneeId(),
                workItem.getNickname(),
                workItem.getProfileImageUrl()
        );
    }
}
