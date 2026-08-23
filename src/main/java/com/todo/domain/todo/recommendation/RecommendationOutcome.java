package com.todo.domain.todo.recommendation;

/**
 * 핸들러가 돌려주는 결과의 종류. 명령어 인프라의 핸들러 계약은 결과를 "거부"할 수 없으므로
 * rate limit·데이터 없음·기능 꺼짐도 전부 결과의 한 종류로 표현한다.
 */
public enum RecommendationOutcome {
    /** 전체 분석 결과, 추천 1개 이상 */
    READY,
    /** 분석은 했지만 제안할 게 없음 */
    EMPTY,
    /** 기록이 적어 시작용 추천만 */
    STARTER,
    /** 기록이 전혀 없어 모델을 부르지 않음 */
    NONE,
    /** 최근 추천이 있거나 한도 초과. {@code previousMessageId}로 직전 카드를 가리킨다 */
    COOLDOWN,
    /** 기능 꺼짐 또는 OpenAI 키 없음 */
    UNAVAILABLE
}
