package com.todo.domain.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "투두 기간 리포트 기간 정보")
public record TodoReportPeriodResponse(

        @Schema(description = "조회 시작일", example = "2026-05-20")
        LocalDate startDate,

        @Schema(description = "조회 종료일", example = "2026-05-26")
        LocalDate endDate,

        @Schema(description = "조회 날짜 수", example = "7")
        int dateCount
) {
    public static TodoReportPeriodResponse from(LocalDate startDate, LocalDate endDate, int dateCount) {
        return new TodoReportPeriodResponse(startDate, endDate, dateCount);
    }
}
