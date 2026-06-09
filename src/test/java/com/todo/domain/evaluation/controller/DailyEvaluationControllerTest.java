package com.todo.domain.evaluation.controller;

import com.todo.domain.evaluation.dto.response.DailyEvaluationResponse;
import com.todo.domain.evaluation.service.DailyEvaluationService;
import com.todo.domain.team.entity.AiPersona;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DailyEvaluationControllerTest {

    @Mock
    private DailyEvaluationService dailyEvaluationService;

    @Test
    void 일일평가_응답을_반환한다() {
        DailyEvaluationController controller = new DailyEvaluationController(dailyEvaluationService);
        DailyEvaluationResponse serviceResponse = new DailyEvaluationResponse(
                LocalDate.of(2026, 6, 4),
                AiPersona.ANGEL,
                "좋아요"
        );
        given(dailyEvaluationService.getDailyEvaluation(1L, "user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<DailyEvaluationResponse>> response =
                controller.getDailyEvaluation(1L, new TestingAuthenticationToken("user1", null));

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }
}
