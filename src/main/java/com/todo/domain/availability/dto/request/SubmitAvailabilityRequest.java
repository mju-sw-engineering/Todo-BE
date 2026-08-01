package com.todo.domain.availability.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitAvailabilityRequest(
        @NotNull
        @Valid
        @Schema(description = "가능한 시간 슬롯 목록. 빈 배열이면 기존 응답 전체 취소.")
        List<SlotItem> slots
) {}
