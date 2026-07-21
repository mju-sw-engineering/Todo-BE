package com.todo.domain.evaluation.dto.response;

import com.todo.domain.team.entity.AiPersona;

public record AiDailyEvaluationResponse(
        AiPersona persona,
        String message
) {}
