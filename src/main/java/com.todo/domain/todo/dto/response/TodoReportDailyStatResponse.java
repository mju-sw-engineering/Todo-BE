package com.todo.domain.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "투두 기간 리포트 일별 통계")
public record TodoReportDailyStatResponse(

        @Schema(description = "통계 날짜", example = "2026-05-20")
        LocalDate date,

        @Schema(description = "전체 할 일 수", example = "3")
        int totalTodoCount,

        @Schema(description = "성공한 할 일 수", example = "2")
        int successCount,

        @Schema(description = "실패한 할 일 수", example = "1")
        int failCount,

        @Schema(description = "진행 중인 할 일 수", example = "0")
        int inProgressCount,

        @Schema(description = "성공률", example = "66")
        Integer achievementRate
) {}
