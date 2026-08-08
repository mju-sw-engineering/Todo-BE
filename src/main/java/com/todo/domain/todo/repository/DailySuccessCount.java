package com.todo.domain.todo.repository;

import java.time.LocalDate;

/**
 * 사용자의 일별 완료(SUCCESS) 작업 개수 집계 프로젝션.
 */
public interface DailySuccessCount {
    LocalDate getDay();

    long getCount();
}
