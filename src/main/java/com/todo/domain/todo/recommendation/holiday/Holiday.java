package com.todo.domain.todo.recommendation.holiday;

import java.time.LocalDate;

/**
 * 공휴일 하루. 이름은 추천 입력(digest)에 "10-03(토) 개천절"처럼 그대로 실린다.
 */
public record Holiday(LocalDate date, String name) {
}
