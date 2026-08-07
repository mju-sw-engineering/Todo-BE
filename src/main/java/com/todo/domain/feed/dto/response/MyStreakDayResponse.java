package com.todo.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record MyStreakDayResponse(
        @Schema(description = "날짜") LocalDate date,
        @Schema(description = "그날 기록을 남긴 서로 다른 투두 수") int count
) {
    public static MyStreakDayResponse from(LocalDate date, int count) {
        return new MyStreakDayResponse(date, count);
    }
}
