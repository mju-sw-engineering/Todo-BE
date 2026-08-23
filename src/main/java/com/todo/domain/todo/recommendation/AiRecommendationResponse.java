package com.todo.domain.todo.recommendation;

import java.util.List;

/**
 * 모델 출력을 그대로 받는 형태. 필드명은 스키마의 snake_case와 같다 ({@code ProofAnalysisService.VerdictResponse} 관례).
 * 검증·보정을 거치기 전이므로 이 값을 직접 쓰지 않고 {@link RecommendationResultSanitizer}에 넘긴다.
 *
 * @param observations 데이터에서 본 패턴. 추천을 관찰에 묶기 위한 필드라 저장하지 않고 debug 로그로만 남긴다
 */
public record AiRecommendationResponse(
        String observations,
        String greeting,
        List<Item> recommendations
) {
    public record Item(
            String kind,
            String title,
            String description,
            String reason,
            String suggested_deadline,
            Long related_todo_id,
            List<Long> suggested_assignee_ids
    ) {
    }
}
