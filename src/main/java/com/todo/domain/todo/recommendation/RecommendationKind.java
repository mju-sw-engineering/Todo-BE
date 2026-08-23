package com.todo.domain.todo.recommendation;

/** 추천 한 건의 종류. 프롬프트의 정의와 1:1이며 strict 스키마의 enum으로 쓰인다. */
public enum RecommendationKind {
    /** 반복 실패한 큰 일을 첫 단계만 떼어냄 */
    SPLIT,
    /** 한 번 실패한 일을 현실적인 마감으로 재도전 */
    RETRY,
    /** 팀 목적에 맞는 새 일 */
    NEW,
    /** 미배정·특정인 과부하 완화 */
    REBALANCE;

    /** strict enum이라 정상 경로에서는 벗어날 수 없지만, 스키마를 완화하거나 모델을 바꿨을 때를 대비한다. */
    public static RecommendationKind fromModelValue(String raw) {
        if (raw == null) {
            return NEW;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEW;
        }
    }
}
