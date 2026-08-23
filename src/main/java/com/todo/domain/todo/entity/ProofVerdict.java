package com.todo.domain.todo.entity;

/**
 * 제출물이 할 일과 부합하는지에 대한 모델의 판단.
 *
 * <p>불이익(제출자 알림)은 {@link #MISMATCH}에만 따른다. 애매한 건은 {@link #UNCERTAIN}으로
 * 떨어지게 프롬프트를 설계했다. 무임승차를 막는 게 목적이지 오탐으로 팀원을 잡는 게
 * 목적이 아니기 때문이다.
 */
public enum ProofVerdict {
    /** 제출물이 할 일과 부합한다. "AI 확인됨" 뱃지가 붙는다. */
    VERIFIED,
    /** 할 일과 명백히 무관하다. 제출자 본인에게만 조용히 알린다. */
    MISMATCH,
    /** 판단 근거가 부족하다. 뱃지도 알림도 없는 안전 기본값이다. */
    UNCERTAIN
}
