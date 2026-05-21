package com.todo.domain.evaluation.controller;

import com.todo.domain.evaluation.dto.response.DailyEvaluationResponse;
import com.todo.domain.evaluation.service.DailyEvaluationService;
import com.todo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class DailyEvaluationController implements DailyEvaluationControllerDocs {

    private final DailyEvaluationService dailyEvaluationService;

    @GetMapping("/{teamId}/daily-evaluation")
    public ResponseEntity<ApiResponse<DailyEvaluationResponse>> getDailyEvaluation(
            @PathVariable Long teamId,
            Authentication authentication
    ) {
        String loginId = authentication.getName();
        DailyEvaluationResponse response = dailyEvaluationService.getDailyEvaluation(teamId, loginId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
