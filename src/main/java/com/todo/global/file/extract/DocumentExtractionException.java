package com.todo.global.file.extract;

/**
 * 문서에서 텍스트를 뽑지 못했다. 깨진 파일, 암호가 걸린 PDF, 형식과 다른 내용 등이 원인이며
 * 모두 다시 시도해도 같은 결과가 나온다. 호출부는 이 예외를 영구 실패로 처리해야 한다.
 */
public class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentExtractionException(String message) {
        super(message, null);
    }
}
