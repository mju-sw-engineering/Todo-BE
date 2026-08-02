package com.todo.domain.todo.dto.response;

import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "투두 인증 사진 이모지 반응 응답")
public record TodoReactionResponse(

        @Schema(description = "이모지 반응 종류", example = "LIKE")
        TodoReactionType type,

        @Schema(description = "이모지", example = "👍")
        String emoji,

        @Schema(description = "반응 수", example = "3")
        long count
) {
    public static TodoReactionResponse from(TodoReaction reaction, long count) {
        return from(reaction.getReactionType(), count);
    }

    public static TodoReactionResponse from(TodoReactionType type, long count) {
        return new TodoReactionResponse(type, type.getEmoji(), count);
    }
}
