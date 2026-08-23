package com.todo.domain.todo.entity;

public enum ProofAnalysisStatus {
    /** 분석 대기 또는 재시도 대기. 폴러가 집어갈 대상이다. */
    PENDING,
    /** 분석 완료. verdict와 summary가 채워져 있다. */
    DONE,
    /** 재시도를 소진했거나 영구 실패. 뱃지가 붙지 않을 뿐 제출 자체는 유효하다. */
    FAILED,
    /** 분석 대상이 아님. HWP처럼 내용을 읽을 수 없는 형식이 여기 해당한다. */
    SKIPPED
}
