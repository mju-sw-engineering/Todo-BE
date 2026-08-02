package com.todo.domain.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Schema(description = "TASK Todo의 개별 작업 생성 요청")
public record CreateTodoTaskRequest(

        @NotBlank
        @Schema(description = "작업 제목", example = "회의록 작성")
        String title,

        @Schema(description = "작업 설명", example = "회의 기록 정리")
        String description,

        @NotNull
        @Schema(description = "담당자 ID", example = "1")
        Long assigneeId,

        @NotNull
        @Schema(description = "작업 마감 시간 (ISO-8601 오프셋 포함)", example = "2026-08-10T15:00:00+09:00")
        OffsetDateTime deadline
) {}
