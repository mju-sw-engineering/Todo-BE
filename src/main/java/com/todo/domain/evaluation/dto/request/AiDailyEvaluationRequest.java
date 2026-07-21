package com.todo.domain.evaluation.dto.request;

import com.todo.domain.team.entity.AiPersona;
import com.todo.domain.todo.entity.TodoStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiDailyEvaluationRequest(
        Long teamId,
        LocalDate date,
        LocalDate basisDate,
        boolean fallback,
        AiPersona persona,
        Summary summary,
        List<TodoInfo> todos
) {
    public record Summary(
            long totalTodoCount,
            long successCount,
            long failCount,
            long inProgressCount,
            int achievementRate,
            int continuousTodoCount
    ) {}

    public record TodoInfo(
            String title,
            TodoStatus status,
            LocalDateTime deadline,
            long achievementCount,
            long participantCount
    ) {}
}
