package com.todo.domain.todo.recommendation.dto;

import com.todo.domain.todo.recommendation.RecommendationOutcome;

import java.util.List;

/**
 * `/할일추천` 핸들러의 반환값. 명령어 인프라가 JSON으로 직렬화해 {@code result_json}에 저장한다.
 *
 * <p>고정 문구(NONE·COOLDOWN·UNAVAILABLE)는 모델이 아니라 여기서 정한다. 모델을 부르지 않는
 * 경로에서도 카드 모양이 같아야 FE가 {@code outcome} 하나로 분기할 수 있다.
 *
 * @param previousMessageId COOLDOWN일 때 직전 카드의 채팅 메시지 id. 그 외에는 null
 */
public record TeamTodoRecommendationResult(
        RecommendationOutcome outcome,
        String greeting,
        Long previousMessageId,
        List<TeamTodoRecommendationItem> items
) {
    static final String NONE_GREETING =
            "아직 팀 기록이 없어서 살펴볼 게 없어요. 첫 할 일을 하나 만들어볼까요? 기록이 쌓이면 패턴을 찾아드릴게요";
    static final String EMPTY_GREETING = "지금은 잘 굴러가고 있어요. 새로 제안할 건 없어요";
    static final String COOLDOWN_GREETING = "방금 추천한 게 있어요. 위 카드를 확인해 주세요";
    static final String UNAVAILABLE_GREETING = "지금은 추천 기능을 쓸 수 없어요. 잠시 후 다시 시도해 주세요";

    public static TeamTodoRecommendationResult ready(String greeting, List<TeamTodoRecommendationItem> items) {
        if (items.isEmpty()) {
            return empty(greeting);
        }
        return new TeamTodoRecommendationResult(RecommendationOutcome.READY, greeting, null, List.copyOf(items));
    }

    public static TeamTodoRecommendationResult starter(String greeting, List<TeamTodoRecommendationItem> items) {
        if (items.isEmpty()) {
            return empty(greeting);
        }
        return new TeamTodoRecommendationResult(RecommendationOutcome.STARTER, greeting, null, List.copyOf(items));
    }

    public static TeamTodoRecommendationResult empty(String greeting) {
        String text = greeting == null || greeting.isBlank() ? EMPTY_GREETING : greeting;
        return new TeamTodoRecommendationResult(RecommendationOutcome.EMPTY, text, null, List.of());
    }

    public static TeamTodoRecommendationResult none() {
        return new TeamTodoRecommendationResult(RecommendationOutcome.NONE, NONE_GREETING, null, List.of());
    }

    public static TeamTodoRecommendationResult cooldown(Long previousMessageId) {
        return new TeamTodoRecommendationResult(RecommendationOutcome.COOLDOWN, COOLDOWN_GREETING, previousMessageId, List.of());
    }

    public static TeamTodoRecommendationResult unavailable() {
        return new TeamTodoRecommendationResult(RecommendationOutcome.UNAVAILABLE, UNAVAILABLE_GREETING, null, List.of());
    }

    public TeamTodoRecommendationResult withItem(int index, TeamTodoRecommendationItem replacement) {
        List<TeamTodoRecommendationItem> updated = new java.util.ArrayList<>(items);
        updated.set(index, replacement);
        return new TeamTodoRecommendationResult(outcome, greeting, previousMessageId, List.copyOf(updated));
    }
}
