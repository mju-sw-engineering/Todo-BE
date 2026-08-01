package com.todo.domain.availability.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitAvailabilityRequest(
        @NotNull
        @Valid
        @Schema(
                description = "가능한 1시간 단위 슬롯 목록. 빈 배열([])이면 기존 응답 전체 취소.",
                example = "[{\"date\":\"2026-08-04\",\"hour\":9},{\"date\":\"2026-08-04\",\"hour\":10},{\"date\":\"2026-08-05\",\"hour\":14}]"
        )
        List<SlotItem> slots
) {}
