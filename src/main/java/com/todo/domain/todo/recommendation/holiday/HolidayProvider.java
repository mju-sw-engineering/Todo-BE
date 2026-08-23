package com.todo.domain.todo.recommendation.holiday;

import java.time.LocalDate;
import java.util.List;

/**
 * 기간 안의 공휴일을 돌려준다. 구현체가 어디서 가져오든(공공 API, 정적 파일) 호출부는 모른다.
 *
 * <p>실패는 예외가 아니라 <b>빈 목록</b>이다. 공휴일은 추천 품질을 조금 올리는 보조 입력이지
 * 추천 자체의 전제가 아니므로, 조회가 실패해도 추천은 진행돼야 한다. 호출부가 실패 여부를
 * 구분해 문구를 바꾸고 싶으면 {@link #isAvailable()}로 확인한다.
 */
public interface HolidayProvider {

    /** {@code from}·{@code to}를 포함하는 구간의 공휴일을 날짜순으로. 실패 시 빈 목록. */
    List<Holiday> holidaysBetween(LocalDate from, LocalDate to);

    /** 설정(서비스 키)이 있어 조회를 시도할 수 있는 상태인지. 최근 호출의 성공 여부와는 무관하다. */
    boolean isAvailable();
}
