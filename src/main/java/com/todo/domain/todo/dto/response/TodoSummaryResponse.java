package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "투두 요약 응답")
public record TodoSummaryResponse(

        @Schema(description = "투두 ID") Long todoId,
        @Schema(description = "Todo 실행 방식") TodoMode mode,
        @Schema(description = "제목") String title,
        @Schema(description = "마감 시간") OffsetDateTime deadline,
        @Schema(description = "공통 투두 상태") TodoStatus status,
        @Schema(description = "달성 항목 수 (성공 / 전체)") String achievementCount,
        @Schema(description = "내 WorkItem 상태 요약") MyWorkSummaryResponse myWorkSummary
) {
    public static TodoSummaryResponse from(
            Todo todo,
            OffsetDateTime deadline,
            String achievementCount,
            MyWorkSummaryResponse myWorkSummary
    ) {
        return new TodoSummaryResponse(
                todo.getId(),
                todo.getMode(),
                todo.getTitle(),
                deadline,
                todo.getStatus(),
                achievementCount,
                myWorkSummary
        );
    }
}
