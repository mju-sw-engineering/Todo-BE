package com.todo.domain.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "미배정 Todo WorkItem 재배정 요청")
public record AssignTodoWorkItemRequest(

        @NotNull
        @Schema(description = "새 담당자 ID", example = "3")
        Long assigneeId
) {}
