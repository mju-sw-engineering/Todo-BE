package com.todo.domain.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotBlank(message = "팀 이름을 입력해주세요")
        @Schema(description = "팀 이름", example = "우리팀")
        String teamName,

        @Size(max = 100, message = "팀 설명은 100자 이내로 입력해주세요")
        @Schema(description = "팀 설명 (선택)", example = "매일 한 문제씩 푸는 알고리즘 스터디")
        String description,

        @Schema(description = "팀 이미지 object key (presigned-upload로 발급 후 전달)", example = "teams/temp/1/uuid.png")
        String teamImageKey
) {
}
