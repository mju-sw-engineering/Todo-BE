package com.todo.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record HiveArchiveMonthResponse(
        @Schema(description = "연도", example = "2026")
        int year,

        @Schema(description = "월(1~12)", example = "7")
        int month,

        @Schema(description = "꿀을 채운 날 수(1개 이상 완료한 날)", example = "31")
        int filledDays,

        @Schema(description = "그 달의 전체 일수", example = "31")
        int totalDays
) {
    public static HiveArchiveMonthResponse of(int year, int month, int filledDays, int totalDays) {
        return new HiveArchiveMonthResponse(year, month, filledDays, totalDays);
    }
}
