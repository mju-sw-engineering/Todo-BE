package com.todo.domain.todo.recommendation;

/**
 * 추천 한 건의 종류. 프롬프트의 정의와 1:1이며 strict 스키마의 enum으로 쓰인다.
 *
 * <p>셋 다 결과물은 같다 — <b>새 할 일 하나</b>. 종류는 왜 그 일을 제안했는지를 나타내는
 * 꼬리표일 뿐이며, 어느 것도 기존 투두를 수정하지 않는다.
 */
public enum RecommendationKind {
    /** 반복 실패한 큰 일을 첫 단계만 떼어냄 */
    SPLIT,
    /** 한 번 실패한 일을 현실적인 마감으로 재도전 */
    RETRY,
    /** 팀 목적에 맞는 새 일 */
    NEW;

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
