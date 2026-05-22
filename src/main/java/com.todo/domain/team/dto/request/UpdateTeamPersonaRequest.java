package com.todo.domain.team.dto.request;

import com.todo.domain.team.entity.AiPersona;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateTeamPersonaRequest(
        @NotNull(message = "AI 평가 페르소나를 입력해주세요")
        @Schema(description = "AI 평가 페르소나", example = "DEVIL", allowableValues = {"ANGEL", "DEVIL"})
        AiPersona aiPersona
) {
}
