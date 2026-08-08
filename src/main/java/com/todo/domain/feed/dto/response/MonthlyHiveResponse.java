package com.todo.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MonthlyHiveResponse(
        @Schema(description = "연도", example = "2026")
        int year,

        @Schema(description = "월(1~12)", example = "8")
        int month,

        @Schema(description = "1일부터 말일까지의 꿀 진하기. 0=완료 없음, 1=1개, 2=2개, 3=3개 이상, 아직 오지 않은 날은 null",
                example = "[2, 1, 3, 0, 2, null, null]")
        List<Integer> dayLevels,

        @Schema(description = "오늘 기준 연속으로 꿀을 채운 일수", example = "5")
        int currentStreak
) {
    public static MonthlyHiveResponse of(int year, int month, List<Integer> dayLevels, int currentStreak) {
        return new MonthlyHiveResponse(year, month, dayLevels, currentStreak);
    }
}
