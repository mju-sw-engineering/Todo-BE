package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "투두 생성 응답")
public record CreateTodoResponse(

        @Schema(description = "투두 ID") Long todoId,
        @Schema(description = "Todo 실행 방식") TodoMode mode,
        @Schema(description = "투두 제목") String title,
        @Schema(description = "마감 시간") OffsetDateTime deadline,
        @Schema(description = "투두 상태") TodoStatus status,
        @Schema(description = "DIRECT 담당자 목록") List<TodoDirectAssigneeResponse> directAssignees,
        @Schema(description = "TASK 작업 목록") List<TodoTaskResponse> tasks
) {
    public static CreateTodoResponse from(
            Todo todo,
            OffsetDateTime deadline,
            List<TodoDirectAssigneeResponse> directAssignees,
            List<TodoTaskResponse> tasks
    ) {
        return new CreateTodoResponse(
                todo.getId(),
                todo.getMode(),
                todo.getTitle(),
                deadline,
                todo.getStatus(),
                directAssignees,
                tasks
        );
    }
}
