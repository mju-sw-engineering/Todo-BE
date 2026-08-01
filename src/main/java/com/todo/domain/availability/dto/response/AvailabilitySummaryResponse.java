package com.todo.domain.availability.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record AvailabilitySummaryResponse(
        @Schema(description = "투표 ID") Long pollId,
        @Schema(description = "제목") String title,
        @Schema(description = "투표 날짜 목록") List<LocalDate> dateOptions,
        @Schema(description = "시간 범위 시작 (포함)") int startHour,
        @Schema(description = "시간 범위 끝 (미포함)") int endHour,
        @Schema(description = "전체 팀원 수") long totalMemberCount,
        @Schema(description = "응답한 팀원 수") long respondedCount,
        @Schema(description = "전원 응답 여부") boolean allResponded,
        @Schema(description = "내 응답 슬롯 목록") List<AvailabilitySlotItem> mySlots,
        @Schema(description = "히트맵 슬롯 목록 (응답자 있는 슬롯만)") List<HeatmapSlotResponse> heatmap,
        @Schema(description = "최적 슬롯 (없으면 null)") BestSlotResponse bestSlot
) {}
