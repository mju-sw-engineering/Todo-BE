package com.todo.domain.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "투두 기간 리포트 전체 요약")
public record TodoReportSummaryResponse(

        @Schema(description = "전체 할 일 수", example = "10")
        int totalTodoCount,

        @Schema(description = "성공한 할 일 수", example = "7")
        int successCount,

        @Schema(description = "실패한 할 일 수", example = "2")
        int failCount,

        @Schema(description = "진행 중인 할 일 수", example = "1")
        int inProgressCount,

        @Schema(description = "성공률", example = "70")
        Integer achievementRate
) {
    public static TodoReportSummaryResponse from(
            int totalTodoCount,
            int successCount,
            int failCount,
            int inProgressCount,
            Integer achievementRate
    ) {
        return new TodoReportSummaryResponse(
                totalTodoCount,
                successCount,
                failCount,
                inProgressCount,
                achievementRate
        );
    }
}
