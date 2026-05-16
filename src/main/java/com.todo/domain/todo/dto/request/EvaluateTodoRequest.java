package com.todo.domain.todo.dto.request;

import com.todo.domain.todo.entity.VoteType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "팀원 상호 평가 요청")
public record EvaluateTodoRequest(

        @NotNull
        @Schema(description = "피평가자 유저 ID", example = "2")
        Long targetUserId,

        @NotNull
        @Schema(description = "투표 종류 (POSITIVE / NEGATIVE)", example = "POSITIVE")
        VoteType voteType
) {}
