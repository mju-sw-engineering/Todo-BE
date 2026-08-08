package com.todo.domain.feed.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record TeamWeekRhythmResponse(
        @Schema(description = "그 주 월요일 날짜") LocalDate startDate,
        @Schema(description = "월~일 7개. 그날 기록을 남긴 팀원 수, 아직 오지 않은 날은 null") List<Integer> counts
) {
    public static TeamWeekRhythmResponse from(LocalDate startDate, List<Integer> counts) {
        return new TeamWeekRhythmResponse(startDate, counts);
    }
}
