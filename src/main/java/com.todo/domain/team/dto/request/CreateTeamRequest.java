package com.todo.domain.team.dto.request;

import com.todo.domain.team.entity.AiPersona;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(
        @NotBlank(message = "팀 이름을 입력해주세요")
        @Schema(description = "팀 이름", example = "우리팀")
        String teamName,

        @Schema(description = "팀 이미지 object key (presigned-upload로 발급 후 전달)", example = "teams/temp/1/uuid.png")
        String teamImageKey,

        @Schema(description = "AI 평가 페르소나", example = "DEVIL", allowableValues = {"ANGEL", "DEVIL"})
        AiPersona aiPersona
) {
    public CreateTeamRequest(String teamName, String teamImageKey) {
        this(teamName, teamImageKey, null);
    }
}
