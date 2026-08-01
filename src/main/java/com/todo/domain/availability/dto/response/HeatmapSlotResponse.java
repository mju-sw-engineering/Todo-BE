package com.todo.domain.availability.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record HeatmapSlotResponse(
        @Schema(description = "날짜") LocalDate date,
        @Schema(description = "시") int hour,
        @Schema(description = "응답 인원 수") int count,
        @Schema(description = "응답 팀원 닉네임 목록") List<String> members
) {}
