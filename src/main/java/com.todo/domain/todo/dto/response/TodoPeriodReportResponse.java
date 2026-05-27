package com.todo.domain.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "투두 기간 리포트 응답")
public record TodoPeriodReportResponse(

        @Schema(description = "조회 기간")
        TodoReportPeriodResponse period,

        @Schema(description = "기간 전체 요약")
        TodoReportSummaryResponse summary,

        @Schema(description = "가장 부진한 날짜")
        TodoReportDailyStatResponse weakestDay,

        @Schema(description = "일별 통계")
        List<TodoReportDailyStatResponse> dailyStats,

        @Schema(description = "오늘 확인할 액션 후보")
        List<TodoReportActionCandidateResponse> actionCandidates
) {
    public static TodoPeriodReportResponse from(
            TodoReportPeriodResponse period,
            TodoReportSummaryResponse summary,
            TodoReportDailyStatResponse weakestDay,
            List<TodoReportDailyStatResponse> dailyStats,
            List<TodoReportActionCandidateResponse> actionCandidates
    ) {
        return new TodoPeriodReportResponse(
                period,
                summary,
                weakestDay,
                dailyStats,
                actionCandidates
        );
    }
}
