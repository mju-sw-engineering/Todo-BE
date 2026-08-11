package com.todo.domain.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record JoinByInviteLinkRequest(
        @NotBlank(message = "초대 링크 토큰을 입력해주세요")
        @Schema(description = "초대 링크의 token 파라미터 값")
        String token
) {}
