package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "투두 상세 응답")
public record TodoDetailResponse(

        @Schema(description = "투두 ID") Long todoId,
        @Schema(description = "Todo 실행 방식") TodoMode mode,
        @Schema(description = "제목") String title,
        @Schema(description = "설명") String description,
        @Schema(description = "마감 시간") OffsetDateTime deadline,
        @Schema(description = "생성자 닉네임") String creatorNickname,
        @Schema(description = "공통 투두 상태") TodoStatus status,
        @Schema(description = "달성 항목 수 (성공 / 전체)") String achievementCount,
        @Schema(description = "DIRECT 담당자 인증 현황") List<TodoDirectAssigneeResponse> directAssignees,
        @Schema(description = "TASK 작업 현황") List<TodoTaskResponse> tasks
) {
    public static TodoDetailResponse from(
            Todo todo,
            OffsetDateTime deadline,
            String creatorNickname,
            String achievementCount,
            List<TodoDirectAssigneeResponse> directAssignees,
            List<TodoTaskResponse> tasks
    ) {
        return new TodoDetailResponse(
                todo.getId(),
                todo.getMode(),
                todo.getTitle(),
                todo.getDescription(),
                deadline,
                creatorNickname,
                todo.getStatus(),
                achievementCount,
                directAssignees,
                tasks
        );
    }
}
