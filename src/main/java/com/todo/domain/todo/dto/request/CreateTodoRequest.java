package com.todo.domain.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "투두 생성 요청")
public record CreateTodoRequest(

        @NotBlank
        @Schema(description = "투두 제목", example = "알고리즘 문제 풀기")
        String title,

        @Schema(description = "투두 설명 (선택)", example = "백준 골드 1문제 이상")
        String description,

        @NotNull
        @Schema(description = "마감 시간 (ISO-8601 오프셋 포함)", example = "2026-05-21T12:00:00Z")
        OffsetDateTime deadline,

        @Schema(description = "DIRECT Todo 담당자 ID 목록 (tasks와 함께 전달할 수 없음)", example = "[1, 2, 3]")
        List<Long> assigneeIds,

        @Valid
        @Schema(description = "TASK Todo 작업 목록 (assigneeIds와 함께 전달할 수 없음)")
        List<CreateTodoTaskRequest> tasks
) {}
