package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "투두 생성 응답")
public record CreateTodoResponse(

        @Schema(description = "투두 ID") Long todoId,
        @Schema(description = "팀 ID") Long teamId,
        @Schema(description = "생성자 ID") Long creatorId,
        @Schema(description = "투두 제목") String title,
        @Schema(description = "투두 설명") String description,
        @Schema(description = "마감 시간") LocalDateTime deadline,
        @Schema(description = "투두 상태") TodoStatus status,
        @Schema(description = "배정된 팀원 ID 목록") List<Long> assigneeIds,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {
    public static CreateTodoResponse from(Todo todo, List<Long> assigneeIds) {
        return new CreateTodoResponse(
                todo.getId(),
                todo.getTeam().getId(),
                todo.getCreator().getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.getDeadline(),
                todo.getStatus(),
                assigneeIds,
                todo.getCreatedAt()
        );
    }
}
