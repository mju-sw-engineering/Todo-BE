package com.todo.domain.todo.command.dto;

import java.util.List;

public record TeamStatusResult(
        long inProgressCount,
        long successCount,
        long failCount,
        List<InProgressTodo> inProgressTodos
) {

    public record InProgressTodo(
            Long todoId,
            String title,
            long completedWorkItemCount,
            long totalWorkItemCount
    ) {
    }
}
