package com.todo.domain.availability.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateAvailabilityPollRequest(
        @NotBlank
        @Schema(description = "투표 제목", example = "이번주 팀 회의")
        String title,

        @NotEmpty
        @Schema(description = "투표 대상 날짜 목록", example = "[\"2026-07-28\", \"2026-07-29\"]")
        List<LocalDate> dateOptions,

        @NotNull
        @Min(0) @Max(23)
        @Schema(description = "시간 범위 시작 (시, 포함)", example = "9")
        Integer startHour,

        @NotNull
        @Min(1) @Max(24)
        @Schema(description = "시간 범위 끝 (시, 미포함)", example = "21")
        Integer endHour
) {}
