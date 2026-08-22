package com.todo.domain.todo.entity;

import java.util.Set;

/**
 * 인증 파일의 종류. 프론트가 미리보기 방식을 고르는 근거이고, 이후 AI 분석에서
 * 이미지(vision)와 문서(텍스트 추출) 경로를 나누는 기준이기도 하다.
 *
 * <p>확장자가 아니라 contentType으로 판단한다. 확장자는 클라이언트가 붙인 파일명에서
 * 오지만 contentType은 presigned PUT 서명에 포함돼 업로드 시점에 강제된 값이다.
 */
public enum ProofKind {
    IMAGE,
    DOCUMENT;

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    /**
     * @return contentType이 없으면 null. 메타데이터를 저장하기 전에 제출된 기존 행이
     *         여기에 해당한다 — 종류를 단정할 수 없으므로 추측하지 않는다.
     */
    public static ProofKind from(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return IMAGE_CONTENT_TYPES.contains(contentType) ? IMAGE : DOCUMENT;
    }
}
