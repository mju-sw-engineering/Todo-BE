package com.todo.domain.availability.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SlotItem(
        @NotNull
        @Schema(description = "날짜", example = "2026-07-28")
        LocalDate date,

        @NotNull
        @Min(0) @Max(23)
        @Schema(description = "시 (0~23)", example = "10")
        Integer hour
) {}
