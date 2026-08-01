package com.todo.domain.availability.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SlotItem(
        @NotNull
        @Schema(description = "날짜 (투표 생성 시 지정한 dateOptions 중 하나)", example = "2026-08-04")
        LocalDate date,

        @NotNull
        @Min(0) @Max(23)
        @Schema(description = "1시간 블록의 시작 시각 (0~23). hour=9 이면 9:00~10:00 블록.", example = "9")
        Integer hour
) {}
