package com.todo.domain.todo.repository;

import java.time.LocalDate;

/**
 * 피드 집계용 체크인 이벤트 한 건. 체크인은 날짜 단위로 저장되므로 LocalDate를 그대로 쓴다.
 */
public interface CheckInActivityRecord {

    LocalDate getOccurredOn();

    Long getUserId();

    Long getTodoId();
}
