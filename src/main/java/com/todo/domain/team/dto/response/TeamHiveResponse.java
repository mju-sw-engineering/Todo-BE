package com.todo.domain.team.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TeamHiveResponse(
        @Schema(description = "벌집 레벨 (1~4)", example = "3")
        int level,

        @Schema(description = "팀이 함께 모은 누적 기록 수 — 팀 투두에 대한 (팀원, 날짜, 투두) 고유 활동 수", example = "278")
        int totalRecords,

        @Schema(description = "현재 레벨이 시작되는 누적 기록 수", example = "100")
        int currentThreshold,

        @Schema(description = "다음 레벨이 시작되는 누적 기록 수. 최고 레벨이면 null", example = "300")
        Integer nextThreshold
) {
    public static TeamHiveResponse of(int level, int totalRecords, int currentThreshold, Integer nextThreshold) {
        return new TeamHiveResponse(level, totalRecords, currentThreshold, nextThreshold);
    }
}
