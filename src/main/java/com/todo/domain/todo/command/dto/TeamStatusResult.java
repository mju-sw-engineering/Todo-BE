package com.todo.domain.todo.command.dto;

public record TeamStatusResult(
        long inProgressTodoCount,
        long successTodoCount,
        long failTodoCount,
        long inProgressWorkItemTotal,
        long inProgressWorkItemCompletedCount
) {
}
