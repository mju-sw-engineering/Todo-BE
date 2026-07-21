package com.todo.domain.evaluation.dto.response;

import com.todo.domain.evaluation.entity.DailyEvaluation;
import com.todo.domain.team.entity.AiPersona;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record DailyEvaluationResponse(
        @Schema(description = "평가 기준일", example = "2026-05-20")
        LocalDate date,

        @Schema(description = "AI 평가 페르소나", example = "DEVIL")
        AiPersona persona,

        @Schema(description = "AI 평가 메시지")
        String message
) {
    public static DailyEvaluationResponse from(DailyEvaluation evaluation) {
        return new DailyEvaluationResponse(
                evaluation.getEvaluationDate(),
                evaluation.getPersona(),
                evaluation.getMessage()
        );
    }
}
