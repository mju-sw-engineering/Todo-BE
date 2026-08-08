package com.todo.domain.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(description = "오늘 진행한 내용 한 줄", example = "3장 초안까지 정리했어요")
        String memo
) {}
