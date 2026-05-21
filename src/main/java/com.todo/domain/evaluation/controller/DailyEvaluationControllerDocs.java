package com.todo.domain.evaluation.controller;

import com.todo.domain.evaluation.dto.response.DailyEvaluationResponse;
import com.todo.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Daily Evaluation", description = "AI 하루 평가 API")
public interface DailyEvaluationControllerDocs {

    @Operation(summary = "AI 하루 평가 조회", description = "전날 팀 투두 기반 AI 평가를 조회하거나 생성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI 하루 평가 조회 성공",
                    content = @Content(schema = @Schema(implementation = DailyEvaluationResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 접근 권한 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음 또는 전날 투두 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI 서버 호출 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<DailyEvaluationResponse>> getDailyEvaluation(
            Long teamId,
            Authentication authentication
    );
}
