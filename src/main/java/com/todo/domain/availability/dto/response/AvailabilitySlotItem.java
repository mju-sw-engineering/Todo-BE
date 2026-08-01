package com.todo.domain.availability.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record AvailabilitySlotItem(
        @Schema(description = "날짜") LocalDate date,
        @Schema(description = "시") int hour
) {}
