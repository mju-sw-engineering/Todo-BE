package com.todo.global.ai;

/**
 * OpenAI 호출 실패. 재시도로 해소될 수 있는 실패인지를 {@link #isRetryable()}로 구분한다.
 *
 * <p>이 구분이 중요한 이유는 호출부가 폴러이기 때문이다. 죽은 API 키나 스키마 위반처럼
 * 재시도해도 같은 결과가 나오는 실패를 계속 재시도하면 자원만 태우고, 반대로 일시적인
 * 429·타임아웃을 즉시 포기하면 멀쩡한 요청이 버려진다.
 */
public class AiClientException extends RuntimeException {

    private final boolean retryable;

    private AiClientException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** 일시적 실패 — 타임아웃, 429, 5xx. 백오프 후 재시도할 가치가 있다. */
    public static AiClientException retryable(String message, Throwable cause) {
        return new AiClientException(message, true, cause);
    }

    /** 영구적 실패 — 인증 오류, 잘못된 요청, 스키마 위반. 재시도해도 같은 결과다. */
    public static AiClientException permanent(String message, Throwable cause) {
        return new AiClientException(message, false, cause);
    }

    public boolean isRetryable() {
        return retryable;
    }
}
