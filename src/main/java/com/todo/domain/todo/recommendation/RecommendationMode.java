package com.todo.domain.todo.recommendation;

/**
 * 팀 기록의 양에 따라 어떤 프롬프트를 쓸지(또는 아예 부르지 않을지) 가른다.
 *
 * <p>빈 데이터에 모델을 부르면 지어낸 추천이 나온다. 그래서 모델에 줄 재료가 하나도 없을 때는
 * 호출하지 않고({@link #NONE}), 재료가 조금 있을 때는 새 할 일만 제안하게 한다({@link #STARTER}).
 */
public enum RecommendationMode {
    /** 투두 0개이고 팀 설명도 없음. 모델을 부르지 않는다. */
    NONE,
    /** 완료·실패 기록이 {@link TeamActivityDigestBuilder#STARTER_THRESHOLD}개 미만. 팀 설명·진행 중 투두로 새 할 일만 제안. */
    STARTER,
    /** 패턴 분석이 가능한 만큼 기록이 있다. */
    FULL
}
