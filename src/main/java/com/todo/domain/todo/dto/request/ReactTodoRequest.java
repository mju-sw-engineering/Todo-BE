package com.todo.domain.todo.dto.request;

import com.todo.domain.todo.entity.TodoReactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "투두 인증 사진 이모지 반응 요청")
public record ReactTodoRequest(

        @NotNull
        @Schema(description = "이모지 반응 종류", example = "LIKE",
                allowableValues = {"LIKE", "HEART", "SURPRISED", "DISLIKE", "ANGRY"})
        TodoReactionType type
) {}
