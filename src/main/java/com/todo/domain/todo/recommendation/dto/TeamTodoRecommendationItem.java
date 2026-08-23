package com.todo.domain.todo.recommendation.dto;

import com.todo.domain.todo.recommendation.RecommendationKind;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 추천 카드 한 칸. 명령어 실행 결과({@code result_json})에 그대로 직렬화되며, [등록]이 눌리면
 * {@code registered*}만 채워진 복사본으로 교체된다.
 */
public record TeamTodoRecommendationItem(
        int index,
        RecommendationKind kind,
        String title,
        String description,
        String reason,
        OffsetDateTime suggestedDeadline,
        Long relatedTodoId,
        List<Long> suggestedAssigneeIds,
        Long registeredTodoId,
        Long registeredBy,
        String registeredByNickname
) {
    public static TeamTodoRecommendationItem of(
            int index,
            RecommendationKind kind,
            String title,
            String description,
            String reason,
            OffsetDateTime suggestedDeadline,
            Long relatedTodoId,
            List<Long> suggestedAssigneeIds
    ) {
        return new TeamTodoRecommendationItem(
                index, kind, title, description, reason, suggestedDeadline, relatedTodoId,
                List.copyOf(suggestedAssigneeIds), null, null, null);
    }

    public boolean isRegistered() {
        return registeredTodoId != null;
    }

    public TeamTodoRecommendationItem withRegistration(Long todoId, Long userId, String nickname) {
        return new TeamTodoRecommendationItem(
                index, kind, title, description, reason, suggestedDeadline, relatedTodoId,
                suggestedAssigneeIds, todoId, userId, nickname);
    }
}
