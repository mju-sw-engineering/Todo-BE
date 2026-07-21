package com.todo.domain.evaluation.client;

import com.todo.domain.evaluation.dto.request.AiDailyEvaluationRequest;
import com.todo.domain.evaluation.dto.response.AiDailyEvaluationResponse;
import com.todo.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiDailyEvaluationClient {

    private final RestClient restClient;

    public AiDailyEvaluationClient(
            RestClient.Builder restClientBuilder,
            @Value("${ai.server.base-url:https://ai.swe.bluerack.org}") String baseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public AiDailyEvaluationResponse createDailyEvaluation(AiDailyEvaluationRequest request) {
        try {
            AiDailyEvaluationResponse response = restClient.post()
                    .uri("/evaluations/daily")
                    .body(request)
                    .retrieve()
                    .body(AiDailyEvaluationResponse.class);

            if (response == null || response.message() == null || response.message().isBlank()) {
                throw new BusinessException("AI 평가 응답이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException("AI 평가 서버 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }
}
