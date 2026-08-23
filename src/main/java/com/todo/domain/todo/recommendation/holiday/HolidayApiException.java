package com.todo.domain.todo.recommendation.holiday;

/**
 * 공공데이터포털 호출 실패. 연결·타임아웃·비정상 resultCode·파싱 실패를 모두 포함한다.
 * 호출부({@link DataGoKrHolidayProvider})는 종류를 구분하지 않고 빈 결과로 대체하므로
 * 재시도 가능 여부를 나누지 않는다.
 */
public class HolidayApiException extends RuntimeException {

    public HolidayApiException(String message) {
        super(message);
    }

    public HolidayApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
