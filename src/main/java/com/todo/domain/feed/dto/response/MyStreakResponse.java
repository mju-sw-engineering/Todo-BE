package com.todo.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MyStreakResponse(
        @Schema(description = "오래된 날 → 최신 순서. 월요일 시작 16주(112일)") List<MyStreakDayResponse> days,
        @Schema(description = "현재 연속 기록 일수") int currentStreak
) {
    public static MyStreakResponse from(List<MyStreakDayResponse> days, int currentStreak) {
        return new MyStreakResponse(days, currentStreak);
    }
}
