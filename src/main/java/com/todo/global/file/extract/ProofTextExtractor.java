package com.todo.global.file.extract;

/**
 * 인증 문서 한 종류에서 텍스트를 뽑는다. 구현체는 형식별로 하나씩 두고
 * {@link DocumentTextExtractor}가 contentType으로 라우팅한다.
 */
public interface ProofTextExtractor {

    boolean supports(String contentType);

    /**
     * @throws DocumentExtractionException 파일이 깨졌거나 형식과 내용이 다를 때
     */
    String extract(byte[] bytes);
}
